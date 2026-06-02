package uk.co.pdintel.payment.config;

/**
 * Configures the Stripe Java SDK at application startup.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Sets Stripe.apiKey and Stripe.overrideApiBase via @PostConstruct — single
 *       initialisation point for all Stripe SDK configuration.</li>
 *   <li>stripe.base-url is configurable — defaults to https://api.stripe.com in all
 *       real environments; overridden to http://localhost:8089 in the test profile
 *       so WireMock intercepts all Stripe HTTP calls without any code change.</li>
 *   <li>Removes the need for @PostConstruct in StripeCustomerService — SDK config
 *       is a cross-cutting concern, not a per-service responsibility.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.api-key}")
    private String apiKey;

    @Value("${stripe.base-url}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
        Stripe.overrideApiBase(baseUrl);
    }
}
