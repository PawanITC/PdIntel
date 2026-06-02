package uk.co.pdintel.payment.api;

/**
 * REST controller for Stripe subscription management.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Returns 201 CREATED — a new subscription resource was created, not 200 OK.</li>
 *   <li>@Valid on request body — triggers Bean Validation before the method body executes.
 *       Invalid requests are rejected with 400 by GlobalExceptionHandler.</li>
 *   <li>@AuthenticationPrincipal UserPrincipal — Spring injects directly from
 *       SecurityContextHolder; no manual context lookup needed.</li>
 *   <li>Delegates entirely to SubscriptionService — controller has one job:
 *       parse HTTP input, delegate, return HTTP response. Zero business logic here.</li>
 *   <li>@ApiResponses documents all status codes matching the feature file scenarios:
 *       201 (created), 400 (validation), 401 (unauthenticated), 403 (wrong council),
 *       409 (duplicate).</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.pdintel.payment.security.UserPrincipal;
import uk.co.pdintel.payment.service.SubscriptionService;

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Stripe subscription management")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    @Operation(
            summary = "Create a new subscription",
            description = "Creates a Stripe subscription for the authenticated user's council. "
                    + "Returns a clientSecret for the frontend Stripe Payment Element.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subscription created — clientSecret returned"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid request fields"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token"),
            @ApiResponse(responseCode = "403", description = "User is not a member of the requested council"),
            @ApiResponse(responseCode = "409", description = "Subscription already exists for this user and council")
    })
    public ResponseEntity<CreateSubscriptionResponse> createSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateSubscriptionRequest request) {

        CreateSubscriptionResponse response = subscriptionService.createSubscription(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
