package uk.co.pdintel.payment.bdd;

/**
 * Cucumber step definitions for Stripe webhook handling scenarios.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Fixed stripeEventId per scenario — deterministic, same ID reused across
 *       "already processed" and "exactly 1 entry" steps within a scenario.</li>
 *   <li>Payload uses realistic Stripe JSON with id, type, and data.object fields —
 *       sufficient for Webhook.constructEvent() to deserialise without error.</li>
 *   <li>councilId embedded in payload metadata — webhook handler extracts it as
 *       the Kafka partition key when writing to outbox.</li>
 *   <li>POST uses text/plain content type — Stripe sends webhook body as raw UTF-8
 *       bytes, not application/json. TestRestTemplate must match this.</li>
 *   <li>Response time measured with System.nanoTime() — nanosecond precision for
 *       sub-30ms assertions; startTime set just before exchange() to measure
 *       only handler latency, not test setup.</li>
 *   <li>DB repositories injected directly — asserts on actual DB state, not just
 *       HTTP response. Proves outbox was written atomically.</li>
 *   <li>@Before resets WireMock — consistent clean state per scenario.</li>
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import uk.co.pdintel.payment.repository.ProcessedStripeEventRepository;
import uk.co.pdintel.payment.repository.StripeEventOutboxRepository;
import uk.co.pdintel.payment.wiremock.StripeSignatureHelper;
import uk.co.pdintel.payment.wiremock.StripeWireMockStubs;

import static org.assertj.core.api.Assertions.assertThat;

public class WebhookSteps {

    private static final String WEBHOOK_URL      = "/api/v1/webhooks/stripe";
    private static final String STRIPE_EVENT_ID  = "evt_test_bdd_webhook_001";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Autowired
    private StripeEventOutboxRepository stripeEventOutboxRepository;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${auth.mock.council-ids}")
    private String mockCouncilIds;

    private String currentPayload;
    private String stripeSignatureHeader;
    private long responseTimeNanos;

    @Before
    public void resetState() {
        StripeWireMockStubs.reset();
        currentPayload = null;
        stripeSignatureHeader = null;
        responseTimeNanos = 0;
    }

    @Given("a valid Stripe-Signature for event type {string}")
    public void aValidStripeSignatureForEventType(String eventType) {
        String councilId = mockCouncilIds.split(",")[0].trim();
        currentPayload = buildPayload(STRIPE_EVENT_ID, eventType, councilId);
        stripeSignatureHeader = StripeSignatureHelper.buildSignatureHeader(
                currentPayload, webhookSecret);
    }

    @Given("the event has already been processed")
    public void theEventHasAlreadyBeenProcessed() {
        HttpEntity<String> request = buildRequest(currentPayload, stripeSignatureHeader);
        restTemplate.exchange(WEBHOOK_URL, HttpMethod.POST, request, String.class);
    }

    @Given("an invalid Stripe-Signature header")
    public void anInvalidStripeSignatureHeader() {
        currentPayload = buildPayload(STRIPE_EVENT_ID, "invoice.payment_succeeded",
                mockCouncilIds.split(",")[0].trim());
        stripeSignatureHeader = "t=1234567890,v1=invalidsignature";
    }

    @Given("no Stripe-Signature header")
    public void noStripeSignatureHeader() {
        currentPayload = buildPayload(STRIPE_EVENT_ID, "invoice.payment_succeeded",
                mockCouncilIds.split(",")[0].trim());
        stripeSignatureHeader = null;
    }

    @When("I post the webhook payload to {string}")
    public void iPostTheWebhookPayloadTo(String path) {
        HttpEntity<String> request = buildRequest(currentPayload, stripeSignatureHeader);
        long startTime = System.nanoTime();
        scenarioContext.setLastResponse(
                restTemplate.exchange(path, HttpMethod.POST, request, String.class));
        responseTimeNanos = System.nanoTime() - startTime;
    }

    @And("the stripe event outbox contains the event")
    public void theStripeEventOutboxContainsTheEvent() {
        assertThat(stripeEventOutboxRepository.findByStripeEventId(STRIPE_EVENT_ID))
                .isPresent();
    }

    @And("the stripe event outbox contains exactly {int} entry for the event")
    public void theStripeEventOutboxContainsExactlyNEntries(int expectedCount) {
        assertThat(stripeEventOutboxRepository.countByStripeEventId(STRIPE_EVENT_ID))
                .isEqualTo(expectedCount);
    }

    @And("the response time should be less than {int} milliseconds")
    public void theResponseTimeShouldBeLessThan(int maxMillis) {
        long actualMillis = responseTimeNanos / 1_000_000;
        assertThat(actualMillis)
                .as("Response time %dms exceeded maximum of %dms", actualMillis, maxMillis)
                .isLessThan(maxMillis);
    }

    private HttpEntity<String> buildRequest(String payload, String signatureHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        if (signatureHeader != null) {
            headers.set("Stripe-Signature", signatureHeader);
        }
        return new HttpEntity<>(payload, headers);
    }

    private String buildPayload(String eventId, String eventType, String councilId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "type": "%s",
                  "livemode": false,
                  "created": 1700000000,
                  "data": {
                    "object": {
                      "id": "sub_test_001",
                      "object": "subscription",
                      "customer": "cus_test_001",
                      "metadata": {
                        "councilId": "%s"
                      }
                    }
                  }
                }
                """.formatted(eventId, eventType, councilId);
    }
}
