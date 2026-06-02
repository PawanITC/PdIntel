package uk.co.pdintel.payment.wiremock;

/**
 * Test utility for generating valid Stripe-Signature headers in webhook tests.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Real HMAC-SHA256 implementation — Stripe's Webhook.constructEvent() validates
 *       real crypto. We generate a matching signature so tests exercise the actual
 *       verification logic, not a mock.</li>
 *   <li>Stripe signature format: "t=&lt;timestamp&gt;,v1=&lt;hmac&gt;" — exact format
 *       Stripe sends. Any deviation causes 400 even with correct HMAC.</li>
 *   <li>HMAC input is: timestamp + "." + payload — matches Stripe's signing scheme.</li>
 *   <li>Uses whsec_test_dummy secret — matches stripe.webhook-secret in
 *       application-test.yml; safe to use in test code.</li>
 *   <li>buildSignatureHeader() uses current time — fresh timestamp per call avoids
 *       Stripe's 5-minute tolerance window rejection in slow CI environments.</li>
 *   <li>Static methods — pure utility, no state, no Spring bean needed.</li>
 *   <li>javax.crypto only — no extra dependency, standard JDK.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class StripeSignatureHelper {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private StripeSignatureHelper() {
    }

    public static String buildSignatureHeader(String payload, String secret) {
        long timestamp = System.currentTimeMillis() / 1000;
        return buildSignatureHeader(payload, secret, timestamp);
    }

    public static String buildSignatureHeader(String payload, String secret, long timestamp) {
        String hmac = generateHmac(timestamp + "." + payload, secret);
        return "t=" + timestamp + ",v1=" + hmac;
    }

    public static String generateHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Stripe HMAC signature", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
