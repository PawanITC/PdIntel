package uk.co.pdintel.payment.exception;

/**
 * Global exception handler mapping domain exceptions to HTTP responses.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@RestControllerAdvice — applies to all @RestController classes,
 *       returns JSON automatically.</li>
 *   <li>CouncilAccessDeniedException → 403 FORBIDDEN — user is authenticated
 *       but not authorised for this council.</li>
 *   <li>DuplicateSubscriptionException → 409 CONFLICT — resource already exists.</li>
 *   <li>MethodArgumentNotValidException → 400 BAD REQUEST — @Valid failure.
 *       Collects all field errors into an "errors" map so the frontend can
 *       highlight specific fields. Feature file asserts "errors" field exists.</li>
 *   <li>Generic Exception → 500 — catch-all, message hidden from client to
 *       avoid leaking internal details.</li>
 *   <li>ProblemDetail (RFC 7807) — standard HTTP problem response format,
 *       native in Spring 6 / Boot 3.x.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CouncilAccessDeniedException.class)
    public ProblemDetail handleCouncilAccessDenied(CouncilAccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Access Denied");
        return problem;
    }

    @ExceptionHandler(DuplicateSubscriptionException.class)
    public ProblemDetail handleDuplicateSubscription(DuplicateSubscriptionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate Subscription");
        return problem;
    }

    @ExceptionHandler(WebhookSignatureException.class)
    public ProblemDetail handleWebhookSignature(WebhookSignatureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Webhook Signature");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (existing, replacement) -> existing));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
