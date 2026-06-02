package uk.co.pdintel.payment.wiremock;

/**
 * Reusable WireMock stubs for Stripe API endpoints.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Static helper methods — no state, no Spring wiring needed. Step definitions
 *       call these directly without injecting a bean.</li>
 *   <li>Matches exact Stripe API paths (/v1/customers, /v1/subscriptions) — the Stripe
 *       SDK constructs these paths internally; stubs must match exactly.</li>
 *   <li>clientSecret parameterised — each scenario passes a distinct value so
 *       feature file assertions are deterministic and readable.</li>
 *   <li>Minimal JSON responses — only the fields our service code actually reads.
 *       Extra fields are ignored by the Stripe SDK deserialiser.</li>
 *   <li>latest_invoice.payment_intent.client_secret nested structure — mirrors the
 *       real Stripe Subscription response that our service traverses to extract
 *       the client secret for the frontend.</li>
 *   <li>stubCreateCustomerFailure() — available for error scenario coverage when
 *       Stripe returns a 402 (card declined at customer creation).</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class StripeWireMockStubs {

    private StripeWireMockStubs() {
    }

    public static void stubCreateCustomer() {
        stubFor(post(urlEqualTo("/v1/customers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "cus_test_wiremock",
                                  "object": "customer",
                                  "email": "dev@plany.co.uk",
                                  "livemode": false
                                }
                                """)));
    }

    public static void stubCreateSubscription(String clientSecret) {
        stubFor(post(urlEqualTo("/v1/subscriptions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "sub_test_wiremock",
                                  "object": "subscription",
                                  "status": "incomplete",
                                  "customer": "cus_test_wiremock",
                                  "latest_invoice": {
                                    "id": "in_test_wiremock",
                                    "payment_intent": {
                                      "id": "pi_test_wiremock",
                                      "client_secret": "%s",
                                      "status": "requires_payment_method"
                                    }
                                  },
                                  "items": {
                                    "object": "list",
                                    "data": [
                                      {
                                        "id": "si_test_wiremock",
                                        "price": {
                                          "id": "price_basic_monthly",
                                          "unit_amount": 1500,
                                          "currency": "gbp"
                                        }
                                      }
                                    ]
                                  }
                                }
                                """.formatted(clientSecret))));
    }

    public static void stubCreateCustomerFailure() {
        stubFor(post(urlEqualTo("/v1/customers"))
                .willReturn(aResponse()
                        .withStatus(402)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "error": {
                                    "type": "card_error",
                                    "code": "card_declined",
                                    "message": "Your card was declined."
                                  }
                                }
                                """)));
    }

    public static void reset() {
        WireMock.reset();
    }
}
