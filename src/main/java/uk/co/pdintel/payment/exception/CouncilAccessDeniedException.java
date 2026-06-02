package uk.co.pdintel.payment.exception;

/**
 * Thrown when an authenticated user attempts to perform an operation on a council
 * they are not a member of.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Extends RuntimeException — Spring @Transactional rolls back on unchecked
 *       exceptions; no checked exception handling needed at call sites.</li>
 *   <li>Mapped to HTTP 403 by GlobalExceptionHandler — not 401 (unauthenticated)
 *       because the user IS authenticated, just not authorised for this council.</li>
 *   <li>Message includes councilId — useful for debugging without exposing
 *       sensitive data.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
public class CouncilAccessDeniedException extends RuntimeException {

    public CouncilAccessDeniedException(java.util.UUID councilId) {
        super("Access denied to council: " + councilId);
    }
}
