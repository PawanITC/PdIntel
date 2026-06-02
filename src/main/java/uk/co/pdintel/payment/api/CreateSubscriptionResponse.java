package uk.co.pdintel.payment.api;

/**
 * Response DTO returned after successfully creating a Stripe subscription.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Java record — immutable response DTO, written once and serialised.</li>
 *   <li>clientSecret — Stripe PaymentIntent client secret returned to the frontend so it
 *       can call Stripe.js confirmPayment(). Never stored in the database.</li>
 *   <li>subscriptionId — internal UUID for the locally persisted subscription row.
 *       Frontend uses this for status polling or displaying confirmation details.</li>
 *   <li>stripeSubscriptionId — Stripe's sub_xxx ID. Frontend may need this to open
 *       the Stripe Billing Portal.</li>
 *   <li>status — always "incomplete" at this point due to payment_behavior=DEFAULT_INCOMPLETE
 *       (UK PSD2/SCA compliance). Transitions to "active" after the webhook confirms payment.</li>
 *   <li>customerId — Stripe cus_xxx ID. Frontend may use for Billing Portal redirect.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import java.util.UUID;

public record CreateSubscriptionResponse(
        UUID subscriptionId,
        String stripeSubscriptionId,
        String clientSecret,
        String status,
        String customerId
) {
}
