package uk.co.pdintel.payment.bdd;

/**
 * Cucumber step definitions for outbox relay scenarios.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Outbox rows inserted directly via repository — relay tests focus purely on
 *       relay behaviour, not webhook handler. Keeps tests isolated and fast.</li>
 *   <li>ProcessedStripeEvent row inserted before outbox row — FK constraint on
 *       stripe_event_outbox.stripe_event_id requires the parent row to exist first.</li>
 *   <li>OutboxRelayService.runRelay() called directly — avoids @Scheduled timing
 *       non-determinism in tests. Relay logic is tested, not the scheduler.</li>
 *   <li>KafkaTestHelper with 5s timeout — generous but bounded; gives Kafka time
 *       to deliver in slow CI environments without blocking indefinitely.</li>
 *   <li>DB status asserted after relay — proves relay updated the row status,
 *       not just that Kafka received the message.</li>
 *   <li>Max retry threshold = 3 — retry count 1 is below (retried), 5 is above (skipped).</li>
 *   <li>@Before cleans outbox + processed_events tables — clean slate per scenario,
 *       no row leakage between relay scenarios.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import uk.co.pdintel.payment.domain.ProcessedStripeEvent;
import uk.co.pdintel.payment.domain.StripeEventOutbox;
import uk.co.pdintel.payment.repository.ProcessedStripeEventRepository;
import uk.co.pdintel.payment.repository.StripeEventOutboxRepository;
import uk.co.pdintel.payment.service.OutboxRelayService;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OutboxRelaySteps {

    private static final String TOPIC = "plany.stripe.webhook-raw.v1";
    private static final Duration KAFKA_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private StripeEventOutboxRepository stripeEventOutboxRepository;

    @Autowired
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private KafkaTestHelper kafkaTestHelper;

    private String lastPublishedKey;
    private String lastPublishedValue;

    @Before("@relay")
    public void cleanOutbox() {
        stripeEventOutboxRepository.deleteAll();
        processedStripeEventRepository.deleteAll();
    }

    @Given("a PENDING outbox row exists for event {string} of type {string} with councilId {string}")
    public void aPendingOutboxRowExists(String eventId, String eventType, String councilId) {
        insertOutboxRow(eventId, eventType, councilId, "PENDING", 0);
    }

    @Given("a PUBLISHED outbox row exists for event {string} of type {string} with councilId {string}")
    public void aPublishedOutboxRowExists(String eventId, String eventType, String councilId) {
        insertOutboxRow(eventId, eventType, councilId, "PUBLISHED", 0);
    }

    @Given("a FAILED outbox row exists for event {string} of type {string} with councilId {string} and retry count {int}")
    public void aFailedOutboxRowExists(String eventId, String eventType, String councilId,
                                       int retryCount) {
        StripeEventOutbox outbox = insertOutboxRow(eventId, eventType, councilId, "FAILED", 0);
        outbox.setRetryCount(retryCount);
        stripeEventOutboxRepository.save(outbox);
    }

    @When("the outbox relay runs")
    public void theOutboxRelayRuns() {
        outboxRelayService.runRelay();
    }

    @Then("a Kafka message is published to topic {string}")
    public void aKafkaMessageIsPublishedToTopic(String topic) {
        List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> records =
                kafkaTestHelper.consumeMessages(topic, 1, KAFKA_TIMEOUT);
        assertThat(records).isNotEmpty();
        lastPublishedKey = records.get(0).key();
        lastPublishedValue = records.get(0).value();
    }

    @And("the Kafka message partition key is {string}")
    public void theKafkaMessagePartitionKeyIs(String expectedKey) {
        assertThat(lastPublishedKey).isEqualTo(expectedKey);
    }

    @And("the outbox row for event {string} has status {string}")
    public void theOutboxRowForEventHasStatus(String eventId, String expectedStatus) {
        StripeEventOutbox outbox = stripeEventOutboxRepository
                .findByStripeEventId(eventId)
                .orElseThrow(() -> new AssertionError("Outbox row not found for event: " + eventId));
        assertThat(outbox.getStatus()).isEqualTo(expectedStatus);
    }

    @Then("no Kafka message is published for event {string}")
    public void noKafkaMessageIsPublishedForEvent(String eventId) {
        boolean found = kafkaTestHelper.hasMessageWithValueContaining(
                TOPIC, eventId, Duration.ofSeconds(2));
        assertThat(found)
                .as("Expected no Kafka message for event %s but one was found", eventId)
                .isFalse();
    }

    @And("the Kafka message payload contains {string}")
    public void theKafkaMessagePayloadContains(String expectedContent) {
        assertThat(lastPublishedValue).contains(expectedContent);
    }

    private StripeEventOutbox insertOutboxRow(String eventId, String eventType,
                                              String councilId, String status, int retryCount) {
        processedStripeEventRepository.save(new ProcessedStripeEvent(eventId, eventType));
        String payload = buildPayload(eventId, eventType, councilId);
        StripeEventOutbox outbox = new StripeEventOutbox(eventId, eventType, payload, councilId);
        outbox.setStatus(status);
        return stripeEventOutboxRepository.save(outbox);
    }

    private String buildPayload(String eventId, String eventType, String councilId) {
        return """
                {
                  "id": "%s",
                  "type": "%s",
                  "data": {
                    "object": {
                      "metadata": {
                        "councilId": "%s"
                      }
                    }
                  }
                }
                """.formatted(eventId, eventType, councilId);
    }
}
