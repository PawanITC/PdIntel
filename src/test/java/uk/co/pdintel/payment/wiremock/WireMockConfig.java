package uk.co.pdintel.payment.wiremock;

/**
 * WireMock server configuration for intercepting Stripe API calls in tests.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Fixed port 8089 — matches stripe.base-url=http://localhost:8089 in
 *       application-test.yml so no dynamic port resolution is needed in stubs.</li>
 *   <li>@TestConfiguration — only loaded in test context, never leaks into main app.</li>
 *   <li>@Bean(destroyMethod="stop") — WireMock server shuts down cleanly when the
 *       Spring test context closes.</li>
 *   <li>WireMock.configureFor() — registers the instance globally so WireMock.reset()
 *       in step definitions works without holding a direct reference to the server.</li>
 *   <li>Single shared instance across all tests — started once per test context,
 *       reset between scenarios via WireMock.reset() in step @Before hooks.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class WireMockConfig {

    public static final int WIREMOCK_PORT = 8089;

    @Bean(destroyMethod = "stop")
    public WireMockServer wireMockServer() {
        WireMockServer server = new WireMockServer(
                WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        server.start();
        WireMock.configureFor("localhost", WIREMOCK_PORT);
        return server;
    }
}
