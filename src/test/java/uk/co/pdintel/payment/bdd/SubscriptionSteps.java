package uk.co.pdintel.payment.bdd;

/**
 * Cucumber step definitions for subscription creation scenarios.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@Before resets WireMock before every scenario — prevents stub pollution
 *       between scenarios.</li>
 *   <li>First councilId from mock config is used as "my council" — deterministic,
 *       always in the authenticated user's membership set.</li>
 *   <li>UNMANAGED_COUNCIL_ID is a fixed UUID not in the mock council list —
 *       guaranteed to produce a 403.</li>
 *   <li>Missing-field scenario uses a mutable HashMap — fields removed by name
 *       to match the Scenario Outline examples table.</li>
 *   <li>"already exists" step makes a real first API call — tests the duplicate
 *       detection through the full stack, not via direct DB insert.</li>
 *   <li>POST requests set Content-Type: application/json explicitly — required
 *       for TestRestTemplate to use Jackson serialisation.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import uk.co.pdintel.payment.wiremock.StripeWireMockStubs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class SubscriptionSteps {

    private static final String SUBSCRIPTIONS_URL = "/api/v1/subscriptions";
    private static final UUID UNMANAGED_COUNCIL_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScenarioContext scenarioContext;

    @Value("${auth.mock.token}")
    private String validToken;

    @Value("${auth.mock.council-ids}")
    private String mockCouncilIds;

    @Before
    public void resetWireMock() {
        StripeWireMockStubs.reset();
    }

    @Given("Stripe will successfully create a customer")
    public void stripeWillSuccessfullyCreateACustomer() {
        StripeWireMockStubs.stubCreateCustomer();
    }

    @Given("Stripe will successfully create a subscription with client secret {string}")
    public void stripeWillSuccessfullyCreateASubscription(String clientSecret) {
        StripeWireMockStubs.stubCreateSubscription(clientSecret);
    }

    @Given("a subscription already exists for my council")
    public void aSubscriptionAlreadyExistsForMyCouncil() {
        UUID councilId = firstCouncilId();
        HttpEntity<Map<String, Object>> request = buildRequest(
                Map.of("planName", "Basic", "priceId", "price_basic_monthly", "councilId", councilId.toString()));
        restTemplate.exchange(SUBSCRIPTIONS_URL, HttpMethod.POST, request, String.class);
    }

    @When("I submit a subscription request for my council with plan {string} and priceId {string}")
    public void iSubmitSubscriptionRequestForMyCouncil(String planName, String priceId) {
        UUID councilId = firstCouncilId();
        HttpEntity<Map<String, Object>> request = buildRequest(
                Map.of("planName", planName, "priceId", priceId, "councilId", councilId.toString()));
        scenarioContext.setLastResponse(
                restTemplate.exchange(SUBSCRIPTIONS_URL, HttpMethod.POST, request, String.class));
    }

    @When("I submit a subscription request for an unmanaged council with plan {string} and priceId {string}")
    public void iSubmitSubscriptionRequestForUnmanagedCouncil(String planName, String priceId) {
        HttpEntity<Map<String, Object>> request = buildRequest(
                Map.of("planName", planName, "priceId", priceId, "councilId", UNMANAGED_COUNCIL_ID.toString()));
        scenarioContext.setLastResponse(
                restTemplate.exchange(SUBSCRIPTIONS_URL, HttpMethod.POST, request, String.class));
    }

    @When("I submit a subscription request with missing {string}")
    public void iSubmitSubscriptionRequestWithMissingField(String missingField) {
        UUID councilId = firstCouncilId();
        Map<String, Object> body = new HashMap<>();
        body.put("planName", "Basic");
        body.put("priceId", "price_basic_monthly");
        body.put("councilId", councilId.toString());
        body.remove(missingField);
        scenarioContext.setLastResponse(
                restTemplate.exchange(SUBSCRIPTIONS_URL, HttpMethod.POST, buildRequest(body), String.class));
    }

    @Then("the response body field {string} equals {string}")
    public void responseBodyFieldEquals(String field, String expectedValue) {
        assertThat(scenarioContext.getLastResponse().getBody())
                .contains("\"" + field + "\":\"" + expectedValue + "\"");
    }

    private UUID firstCouncilId() {
        return UUID.fromString(mockCouncilIds.split(",")[0].trim());
    }

    private HttpEntity<Map<String, Object>> buildRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String authHeader = scenarioContext.getAuthorizationHeader();
        if (authHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }
        return new HttpEntity<>(body, headers);
    }
}
