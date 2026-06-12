package uk.co.pdintel.payment.api;

/**
 * Request DTO for creating a new Stripe subscription.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Java record — immutable DTO, request payloads must not be mutated after deserialisation.</li>
 *   <li>@NotNull on all fields — all three are mandatory; fail fast with HTTP 400 before
 *       touching Stripe or the database.</li>
 *   <li>@NotBlank on planName and priceId — rejects empty strings as well as null.</li>
 *   <li>councilId as UUID — Jackson deserialises UUID from JSON string automatically.
 *       Validated against the authenticated user's councilIds in the service layer (403 if mismatch).</li>
 *   <li>priceId carries the Stripe Price ID (price_xxx) — sent directly to the Stripe API.
 *       Frontend sources this from the pricing page config; server does not derive it.</li>
 *   <li>planName is a human-readable label (Basic, Regional, Enterprise) — stored in
 *       subscriptions.plan_name for display purposes, not used for pricing logic.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to create a Stripe subscription")
public record CreateSubscriptionRequest(

        @Schema(example = "Basic", description = "Plan display name")
        @NotBlank(message = "planName is required")
        String planName,

        @Schema(example = "price_1ThMDvAfNo4hbb7vmgZP76vk", description = "Stripe Price ID from the pricing table (£15/month GBP)")
        @NotBlank(message = "priceId is required")
        String priceId,

        @Schema(example = "123e4567-e89b-12d3-a456-426614174000", description = "Council ID the user manages (dev: use either council from mock token)")
        @NotNull(message = "councilId is required")
        UUID councilId
) {
}
