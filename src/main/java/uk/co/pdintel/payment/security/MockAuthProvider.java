package uk.co.pdintel.payment.security;

/**
 * Mock implementation of AuthProvider for local development and testing.
 *
 * <p>Decisions:
 * <ul>
 *   <li>@Profile("!prod") — cannot activate in production. Missing Cognito bean causes
 *       a hard startup failure rather than a silent security hole.</li>
 *   <li>Token and councilIds are @Value-injected from application-dev/test.yml —
 *       configurable without recompiling.</li>
 *   <li>Returns Optional.empty() for wrong token — correct contract, not an exception.</li>
 *   <li>councilIds parsed to an unmodifiable Set — UserPrincipal councils are never mutated.</li>
 * </ul>
 *
 * <p>Usage: Authorization: Bearer dev-static-token (dev) / test-static-token (test)
 *
 * @author Pawan
 * @copyright 2026
 */
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Profile("!prod")
public class MockAuthProvider implements AuthProvider {

    private static final UUID MOCK_USER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String MOCK_EMAIL  = "dev@plany.co.uk";

    @Value("${auth.mock.token}")
    private String mockToken;

    @Value("${auth.mock.council-ids}")
    private String mockCouncilIds;

    @Override
    public Optional<UserPrincipal> authenticate(String token) {
        if (!mockToken.equals(token)) {
            return Optional.empty();
        }

        Set<UUID> councilIds = Arrays.stream(mockCouncilIds.split(","))
                .map(String::trim)
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());

        return Optional.of(new UserPrincipal(MOCK_USER_ID, MOCK_EMAIL, councilIds));
    }
}
