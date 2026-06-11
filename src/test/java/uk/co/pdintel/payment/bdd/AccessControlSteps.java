package uk.co.pdintel.payment.bdd;

/**
 * Cucumber step definitions for access control consumer scenarios.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Publishes directly via KinesisTestHelper — calls KinesisConsumerService.dispatchRecord()
 *       synchronously. No live stream or LocalStack required.</li>
 *   <li>councilId as partition key — matches relay behaviour.</li>
 *   <li>Awaitility removed — dispatch is synchronous so DB is updated before the step returns.
 *       Assertions can check state immediately, but Awaitility kept for safety margin.</li>
 *   <li>Asserts via UserRepository — source of truth.</li>
 *   <li>lastPublishedPayload stored — "same message published again" re-uses exact payload.</li>
 *   <li>@Before("@access-control") ensures test user exists with clean state.</li>
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
import uk.co.pdintel.payment.domain.User;
import uk.co.pdintel.payment.repository.UserRepository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AccessControlSteps {

    private static final String TEST_USER_EMAIL = "consumer-test@plany.co.uk";
    private static final String EVENT_ID        = "evt_access_control_001";

    @Autowired
    private KinesisTestHelper kinesisTestHelper;

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
    public void aKinesisRecordIsPublished(String ignored, String eventType, String email) {
        String councilId = mockCouncilIds.split(",")[0].trim();
        lastPublishedPayload = buildPayload(EVENT_ID, eventType, email, councilId, null);
        kinesisTestHelper.publish(councilId, lastPublishedPayload);
    }

    @When("a Kafka message is published to {string} with event type {string} with status {string} for user {string}")
    public void aKinesisRecordIsPublishedWithStatus(String ignored, String eventType,
                                                     String status, String email) {
        String councilId = mockCouncilIds.split(",")[0].trim();
        lastPublishedPayload = buildPayload(EVENT_ID, eventType, email, councilId, status);
        kinesisTestHelper.publish(councilId, lastPublishedPayload);
    }

    @And("the same Kafka message is published again")
    public void theSameKinesisRecordIsPublishedAgain() {
        String councilId = mockCouncilIds.split(",")[0].trim();
        kinesisTestHelper.publish(councilId, lastPublishedPayload);
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
