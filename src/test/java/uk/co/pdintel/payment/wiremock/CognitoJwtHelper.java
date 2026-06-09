package uk.co.pdintel.payment.wiremock;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Generates RS256-signed JWTs and stubs the Cognito JWKS + discovery endpoints
 * on the CognitoWireMockBootstrap server.
 *
 * Call stubJwks() once before the Spring context starts (e.g. in a @BeforeAll or
 * static block), then call generateToken(...) per scenario to mint tokens.
 */
public final class CognitoJwtHelper {

    public static final String KID = "cognito-test-key-1";
    public static final String ISSUER = CognitoWireMockBootstrap.ISSUER;

    private static final RSAKey RSA_KEY;

    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).keyID(KID).generate();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private CognitoJwtHelper() {}

    /** Registers OIDC discovery + JWKS stubs on the Cognito WireMock server. */
    public static void stubJwks() {
        try {
            String jwksJson = "{\"keys\":[" + RSA_KEY.toPublicJWK().toJSONString() + "]}";

            String discoveryJson = """
                    {
                      "issuer": "%s",
                      "jwks_uri": "%s/.well-known/jwks.json",
                      "id_token_signing_alg_values_supported": ["RS256"]
                    }
                    """.formatted(ISSUER, ISSUER);

            CognitoWireMockBootstrap.server().stubFor(
                    get(urlEqualTo("/cognito-test/.well-known/openid-configuration"))
                            .willReturn(okJson(discoveryJson)));

            CognitoWireMockBootstrap.server().stubFor(
                    get(urlEqualTo("/cognito-test/.well-known/jwks.json"))
                            .willReturn(okJson(jwksJson)));

        } catch (Exception e) {
            throw new RuntimeException("Failed to stub Cognito JWKS", e);
        }
    }

    /** Mints a valid, signed JWT with the given claims. */
    public static String generateToken(UUID userId, String email, String councilIds) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(userId.toString())
                    .claim("email", email)
                    .claim("custom:councilIds", councilIds)
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .issueTime(new Date())
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                    claims);
            jwt.sign(new RSASSASigner(RSA_KEY));
            return jwt.serialize();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }

    /** Mints a JWT that is already expired. */
    public static String generateExpiredToken(UUID userId, String email) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject(userId.toString())
                    .claim("email", email)
                    .claim("custom:councilIds", "")
                    .expirationTime(new Date(System.currentTimeMillis() - 1000))
                    .issueTime(new Date(System.currentTimeMillis() - 7_200_000))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
                    claims);
            jwt.sign(new RSASSASigner(RSA_KEY));
            return jwt.serialize();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate expired JWT", e);
        }
    }
}
