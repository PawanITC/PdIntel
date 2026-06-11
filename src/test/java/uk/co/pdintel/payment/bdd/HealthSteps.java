package uk.co.pdintel.payment.bdd;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import uk.co.pdintel.payment.config.KinesisMockConfig;
import uk.co.pdintel.payment.wiremock.WireMockConfig;

import static org.assertj.core.api.Assertions.assertThat;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@Import({WireMockConfig.class, KinesisMockConfig.class})
public class HealthSteps {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ScenarioContext scenarioContext;

    @Given("the application is running")
    public void theApplicationIsRunning() {
        // context loaded by @SpringBootTest — this step confirms it
    }

    @When("I request the application health endpoint")
    public void requestHealthEndpoint() {
        scenarioContext.setLastResponse(
                restTemplate.getForEntity("/actuator/health", String.class));
    }

    @Then("the response status should be {int}")
    public void responseStatusShouldBe(int expectedStatus) {
        assertThat(scenarioContext.getLastResponse().getStatusCode().value())
                .isEqualTo(expectedStatus);
    }

    @And("the health status should be {string}")
    public void healthStatusShouldBe(String expectedStatus) {
        assertThat(scenarioContext.getLastResponse().getBody())
                .contains("\"status\":\"" + expectedStatus + "\"");
    }
}
