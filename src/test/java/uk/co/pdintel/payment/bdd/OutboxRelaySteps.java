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
 *   <li>KinesisTestHelper tracks published records in-memory — no live Kinesis
 *       stream or LocalStack required. Relay calls KinesisAsyncClient mock.</li>
 *   <li>DB status asserted after relay — proves relay updated the row status.</li>
 *   <li>Max retry threshold = 3 — retry count 1 is below (retried), 5 is above (skipped).</li>
 *   <li>@Before cleans outbox + processed_events tables — clean slate per scenario.</li>
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class OutboxRelaySteps {

    private static final String STREAM = "pd-payment-kinesis-euw2";

    @Autowired
    private StripeEventOutboxRepository stripeEventOutboxRepository;

    @Autowired
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private KinesisTestHelper kinesisTestHelper;

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
    public void aKinesisRecordIsPublishedToStream(String ignored) {
        List<uk.co.pdintel.payment.config.KinesisMockConfig.CapturedRecord> records =
                kinesisTestHelper.getPublishedRecords();
        assertThat(records).isNotEmpty();
        lastPublishedKey = records.get(records.size() - 1).partitionKey();
        lastPublishedValue = records.get(records.size() - 1).payload();
    }

    @And("the Kafka message partition key is {string}")
    public void theKinesisRecordPartitionKeyIs(String expectedKey) {
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
    public void noKinesisRecordIsPublishedForEvent(String eventId) {
        boolean found = kinesisTestHelper.hasRecordWithPayloadContaining(eventId);
        assertThat(found)
                .as("Expected no Kinesis record for event %s but one was found", eventId)
                .isFalse();
    }

    @And("the Kafka message payload contains {string}")
    public void theKinesisRecordPayloadContains(String expectedContent) {
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
