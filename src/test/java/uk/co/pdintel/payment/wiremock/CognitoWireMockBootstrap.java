package uk.co.pdintel.payment.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Starts a dedicated WireMock server for Cognito JWKS before the Spring context boots.
 *
 * JwtDecoders.fromIssuerLocation() fires during bean construction and immediately
 * fetches /.well-known/openid-configuration then /.well-known/jwks.json. The server
 * must therefore be running before @SpringBootTest starts the application context.
 * A static initialiser guarantees this ordering regardless of test class load order.
 */
public final class CognitoWireMockBootstrap {

    public static final int PORT = 8090;
    public static final String ISSUER = "http://localhost:" + PORT + "/cognito-test";

    private static final WireMockServer SERVER;

    static {
        SERVER = new WireMockServer(WireMockConfiguration.wireMockConfig().port(PORT));
        SERVER.start();
    }

    private CognitoWireMockBootstrap() {}

    public static WireMockServer server() {
        return SERVER;
    }
}
