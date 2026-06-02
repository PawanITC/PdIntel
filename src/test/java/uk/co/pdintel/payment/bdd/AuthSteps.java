package uk.co.pdintel.payment.bdd;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScenarioContext scenarioContext;

    @Value("${auth.mock.token}")
    private String validToken;

    @Given("I have a valid Bearer token")
    public void iHaveAValidBearerToken() {
        scenarioContext.setAuthorizationHeader("Bearer " + validToken);
    }

    @Given("I have an invalid Bearer token")
    public void iHaveAnInvalidBearerToken() {
        scenarioContext.setAuthorizationHeader("Bearer invalid-token-xyz");
    }

    @Given("I have no Authorization header")
    public void iHaveNoAuthorizationHeader() {
        scenarioContext.setAuthorizationHeader(null);
    }

    @When("I request GET {string}")
    public void iRequestGet(String path) {
        HttpHeaders headers = buildHeaders();
        scenarioContext.setLastResponse(
                restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class));
    }

    @When("I request POST {string}")
    public void iRequestPost(String path) {
        HttpHeaders headers = buildHeaders();
        scenarioContext.setLastResponse(
                restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), String.class));
    }

    @And("the response body contains field {string}")
    public void responseBodyContainsField(String field) {
        assertThat(scenarioContext.getLastResponse().getBody())
                .contains("\"" + field + "\"");
    }

    @And("the {string} field contains more than {int} entries")
    public void fieldContainsMoreThanEntries(String field, int minCount) {
        String body = scenarioContext.getLastResponse().getBody();
        assertThat(body).contains("\"" + field + "\"");
        long count = body.chars().filter(c -> c == ',').count();
        assertThat(count).isGreaterThanOrEqualTo(minCount);
    }

    @Then("the response status should not be {int}")
    public void responseStatusShouldNotBe(int unexpectedStatus) {
        assertThat(scenarioContext.getLastResponse().getStatusCode().value())
                .isNotEqualTo(unexpectedStatus);
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
