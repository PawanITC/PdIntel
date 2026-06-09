package uk.co.pdintel.payment.bdd.cognito;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import uk.co.pdintel.payment.bdd.ScenarioContext;
import uk.co.pdintel.payment.wiremock.CognitoJwtHelper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CognitoAuthSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScenarioContext scenarioContext;

    @Given("I have a valid Cognito JWT for user {string} with email {string} and councilIds {string}")
    public void iHaveAValidCognitoJwt(String userId, String email, String councilIds) {
        String token = CognitoJwtHelper.generateToken(UUID.fromString(userId), email, councilIds);
        scenarioContext.setAuthorizationHeader("Bearer " + token);
    }

    @Given("I have an expired Cognito JWT for user {string} with email {string}")
    public void iHaveAnExpiredCognitoJwt(String userId, String email) {
        String token = CognitoJwtHelper.generateExpiredToken(UUID.fromString(userId), email);
        scenarioContext.setAuthorizationHeader("Bearer " + token);
    }

    @Given("I have a tampered Cognito JWT")
    public void iHaveATamperedCognitoJwt() {
        // Valid structure but signature replaced with random bytes — fails JWKS verification
        scenarioContext.setAuthorizationHeader("Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6ImNvZ25pdG8tdGVzdC1rZXktMSJ9.eyJzdWIiOiJ0YW1wZXJlZCIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA5MC9jb2duaXRvLXRlc3QifQ.invalidsignature");
    }

    @Given("I have a random string Bearer token")
    public void iHaveARandomStringBearerToken() {
        scenarioContext.setAuthorizationHeader("Bearer not-a-jwt-at-all");
    }

    @Given("I have no Cognito Authorization header")
    public void iHaveNoCognitoAuthorizationHeader() {
        scenarioContext.setAuthorizationHeader(null);
    }

    @When("I request GET {string} with Cognito auth")
    public void iRequestGetWithCognitoAuth(String path) {
        scenarioContext.setLastResponse(
                restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(buildHeaders()), String.class));
    }

    @When("I request POST {string} with Cognito auth")
    public void iRequestPostWithCognitoAuth(String path) {
        scenarioContext.setLastResponse(
                restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(buildHeaders()), String.class));
    }

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(int expectedStatus) {
        assertThat(scenarioContext.getLastResponse().getStatusCode().value())
                .isEqualTo(expectedStatus);
    }

    @Then("the response status should not be {int}")
    public void theResponseStatusShouldNotBe(int unexpectedStatus) {
        assertThat(scenarioContext.getLastResponse().getStatusCode().value())
                .isNotEqualTo(unexpectedStatus);
    }

    @And("the response body contains field {string}")
    public void theResponseBodyContainsField(String field) {
        assertThat(scenarioContext.getLastResponse().getBody())
                .contains("\"" + field + "\"");
    }

    @And("the response body contains {string}")
    public void theResponseBodyContains(String value) {
        assertThat(scenarioContext.getLastResponse().getBody())
                .contains(value);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String authHeader = scenarioContext.getAuthorizationHeader();
        if (authHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }
        return headers;
    }
}
