package uk.co.pdintel.payment.api;

/**
 * REST controller exposing the current authenticated user's identity.
 *
 * <p>Decisions:
 * <ul>
 *   <li>GET /api/v1/me — natural endpoint for verifying auth is wired correctly;
 *       also useful in production for the frontend to load user context on login.</li>
 *   <li>@AuthenticationPrincipal UserPrincipal — Spring injects directly from
 *       SecurityContextHolder, no manual context lookup needed.</li>
 *   <li>Returns userId, email, and councilIds — all fields from UserPrincipal,
 *       used by frontend to scope council-specific UI.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.co.pdintel.payment.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Identity", description = "Current authenticated user")
public class MeController {

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of(
                "userId",     principal.userId(),
                "email",      principal.email(),
                "councilIds", principal.councilIds()
        ));
    }
}
