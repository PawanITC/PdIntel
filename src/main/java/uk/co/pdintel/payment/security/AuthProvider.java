package uk.co.pdintel.payment.security;

/**
 * Strategy interface for authenticating a raw Bearer token.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Single-method interface — one responsibility: given a token, return who it is or nothing.</li>
 *   <li>Returns Optional&lt;UserPrincipal&gt; — empty means invalid/unrecognised token.
 *       Authentication failure is not exceptional — no checked exceptions.</li>
 *   <li>Input is a raw String token — the filter strips the "Bearer " prefix before calling.
 *       This interface knows nothing about HTTP.</li>
 *   <li>Enables zero-code swap from MockAuthProvider (dev/test) to CognitoAuthProvider (prod)
 *       via Spring @Profile — no changes needed outside the security package.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
import java.util.Optional;

public interface AuthProvider {

    Optional<UserPrincipal> authenticate(String token);
}
