package uk.co.pdintel.payment.bdd;

/**
 * Cucumber step definitions for access control consumer scenarios.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Publishes directly to Kafka via KafkaTemplate — bypasses the relay for focused
 *       consumer testing. Same message format the relay would produce.</li>
 *   <li>councilId as Kafka partition key — matches relay behaviour, ensures consumer
 *       receives messages in council order.</li>
 *   <li>Awaitility for async assertions — consumer processes messages asynchronously.
 *       Polls DB until condition met or 10s timeout rather than Thread.sleep().</li>
 *   <li>Asserts via UserRepository — source of truth. Proves consumer updated the DB,
 *       not just that it received the message.</li>
 *   <li>lastPublishedPayload stored — "same message published again" step re-uses
 *       the exact payload for idempotency testing.</li>
 *   <li>customer.subscription.updated carries status in data.object.status —
 *       consumer reads this field to determine the new subscription status.</li>
 *   <li>@Before("@access-control") ensures test user exists with clean state
 *       before each scenario.</li>
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
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import uk.co.pdintel.payment.domain.User;
import uk.co.pdintel.payment.repository.UserRepository;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessControlSteps {

    private static final String TOPIC          = "plany.stripe.webhook-raw.v1";
    private static final String TEST_USER_EMAIL = "consumer-test@plany.co.uk";
    private static final String EVENT_ID        = "evt_access_control_001";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScenarioContext scenarioContext;

    @Value("${auth.mock.council-ids}")
    private String mockCouncilIds;

    private String lastPublishedPayload;

    @Before("@access-control")
    public void ensureTestUserExists() {
        userRepository.findByEmail(TEST_USER_EMAIL)
                .ifPresent(u -> userRepository.delete(u));
        userRepository.save(new User(TEST_USER_EMAIL));
    }

    @Given("a user exists with email {string} and subscriptionStatus {string}")
    public void aUserExistsWithEmailAndSubscriptionStatus(String email, String status) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(email)));
        user.setSubscriptionStatus(status);
        userRepository.save(user);
    }

    @Given("the user {string} has subscriptionStatus {string}")
    public void theUserHasSubscriptionStatus(String email, String status) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AssertionError("User not found: " + email));
        user.setSubscriptionStatus(status);
        userRepository.save(user);
    }

    @When("a Kafka message is published to {string} with event type {string} for user {string}")
    public void aKafkaMessageIsPublished(String topic, String eventType, String email) {
        String councilId = mockCouncilIds.split(",")[0].trim();
        lastPublishedPayload = buildPayload(EVENT_ID, eventType, email, councilId, null);
        kafkaTemplate.send(topic, councilId, lastPublishedPayload);
    }

    @When("a Kafka message is published to {string} with event type {string} with status {string} for user {string}")
    public void aKafkaMessageIsPublishedWithStatus(String topic, String eventType,
                                                    String status, String email) {
        String councilId = mockCouncilIds.split(",")[0].trim();
        lastPublishedPayload = buildPayload(EVENT_ID, eventType, email, councilId, status);
        kafkaTemplate.send(topic, councilId, lastPublishedPayload);
    }

    @And("the same Kafka message is published again")
    public void theSameKafkaMessageIsPublishedAgain() {
        String councilId = mockCouncilIds.split(",")[0].trim();
        kafkaTemplate.send(TOPIC, councilId, lastPublishedPayload);
    }

    @Then("the subscription status for user {string} should be {string}")
    public void theSubscriptionStatusForUserShouldBe(String email, String expectedStatus) {
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    User user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new AssertionError("User not found: " + email));
                    assertThat(user.getSubscriptionStatus()).isEqualTo(expectedStatus);
                });
    }

    private String buildPayload(String eventId, String eventType, String email,
                                 String councilId, String subscriptionStatus) {
        String statusField = subscriptionStatus != null
                ? "\"status\": \"" + subscriptionStatus + "\","
                : "";
        return """
                {
                  "id": "%s",
                  "type": "%s",
                  "data": {
                    "object": {
                      %s
                      "customer_email": "%s",
                      "metadata": {
                        "councilId": "%s"
                      }
                    }
                  }
                }
                """.formatted(eventId, eventType, statusField, email, councilId);
    }
}
