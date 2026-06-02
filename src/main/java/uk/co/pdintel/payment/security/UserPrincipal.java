package uk.co.pdintel.payment.security;

/**
 * Immutable value object representing an authenticated user in the security context.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Java record — immutable by design, no Lombok needed, concise.</li>
 *   <li>Implements java.security.Principal — plugs natively into Spring Security's
 *       SecurityContext without wrapping; getName() returns email per Principal contract.</li>
 *   <li>Set&lt;UUID&gt; councilIds — a user belongs to multiple councils (flat membership,
 *       no role per council). Set prevents duplicates. UUID matches DB type.</li>
 *   <li>Not a JPA @Entity — this is a runtime security object built from DB data at
 *       authentication time, not a persistent entity.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

public record UserPrincipal(
        UUID userId,
        String email,
        Set<UUID> councilIds
) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
