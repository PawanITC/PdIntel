package uk.co.pdintel.payment.bdd.cognito;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import uk.co.pdintel.payment.wiremock.CognitoJwtHelper;
import uk.co.pdintel.payment.wiremock.WireMockConfig;

import io.cucumber.java.en.Given;

/**
 * Spring context bootstrap for the Cognito BDD suite.
 *
 * CognitoJwtHelper.stubJwks() must run before @SpringBootTest initialises the
 * application context, because CognitoAuthProvider calls
 * JwtDecoders.fromIssuerLocation() during bean construction which immediately
 * fetches the OIDC discovery document. The static initialiser in
 * CognitoWireMockBootstrap starts the WireMock server, and stubJwks() here
 * registers the JWKS stubs before Spring boots.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("cognito-test")
@Testcontainers
@Import(WireMockConfig.class)
public class CognitoHealthSteps {

    static {
        CognitoJwtHelper.stubJwks();
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Given("the Cognito-backed application is running")
    public void theCognitoBackedApplicationIsRunning() {
        // Spring context loaded by @SpringBootTest — step confirms context is up
    }
}
