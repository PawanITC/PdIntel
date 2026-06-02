package uk.co.pdintel.payment.api;

/**
 * REST controller for receiving Stripe webhook events.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@RequestBody String payload — raw String, not a deserialised object.
 *       Stripe-Signature HMAC is computed over the original raw bytes; any
 *       deserialisation (even whitespace changes) would break verification.</li>
 *   <li>@RequestHeader required=false — WebhookService throws WebhookSignatureException
 *       for null/blank header, producing a consistent 400 ProblemDetail response.
 *       Spring's MissingRequestHeaderException (required=true) would produce a
 *       different error shape.</li>
 *   <li>Returns ResponseEntity&lt;Void&gt; with 200 — Stripe only needs HTTP 200.
 *       No body wastes bandwidth on every webhook delivery.</li>
 *   <li>consumes = TEXT_PLAIN — Stripe sends Content-Type: text/plain. Explicit
 *       declaration prevents Spring from rejecting the request with 415.</li>
 *   <li>No @SecurityRequirement — endpoint is public. Stripe-Signature header is
 *       the authentication mechanism; IP allowlisting handled at WAF level.</li>
 *   <li>Must return within 30ms — enforced by WebhookService design (no downstream
 *       calls inside the handler).</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.co.pdintel.payment.service.WebhookService;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Stripe webhook receiver — no JWT required")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(value = "/stripe", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(
            summary = "Receive Stripe webhook event",
            description = "Verifies Stripe-Signature header and writes event to outbox. "
                    + "No JWT required — IP allowlisted at WAF level. Must return within 30ms."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event accepted"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid Stripe-Signature header")
    })
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader) {

        webhookService.handleWebhookEvent(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
