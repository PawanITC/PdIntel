package uk.co.pdintel.payment.exception;

/**
 * Thrown when a Stripe webhook request has a missing or invalid Stripe-Signature header.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Extends RuntimeException — consistent with other domain exceptions.</li>
 *   <li>Mapped to HTTP 400 BAD REQUEST by GlobalExceptionHandler — not 401 or 403.
 *       The request is structurally invalid (tampered or misconfigured), not an
 *       authentication failure in the JWT sense.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
public class WebhookSignatureException extends RuntimeException {

    public WebhookSignatureException(String message) {
        super(message);
    }

    public WebhookSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
