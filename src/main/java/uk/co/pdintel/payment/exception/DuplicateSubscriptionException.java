package uk.co.pdintel.payment.exception;

/**
 * Thrown when a user attempts to create a subscription for a council
 * that already has an active or incomplete subscription.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Extends RuntimeException — consistent with other domain exceptions,
 *       no checked exception handling needed at call sites.</li>
 *   <li>Mapped to HTTP 409 Conflict by GlobalExceptionHandler — semantically
 *       correct: the resource already exists, not a bad request.</li>
 *   <li>Message includes userId and councilId — sufficient for debugging.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
public class DuplicateSubscriptionException extends RuntimeException {

    public DuplicateSubscriptionException(java.util.UUID userId, java.util.UUID councilId) {
        super("Subscription already exists for user " + userId + " and council " + councilId);
    }
}
