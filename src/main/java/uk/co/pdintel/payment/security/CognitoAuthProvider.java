package uk.co.pdintel.payment.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Profile("prod")
public class CognitoAuthProvider implements AuthProvider {

    private final JwtDecoder jwtDecoder;

    @Value("${auth.cognito.user-id-claim:sub}")
    private String userIdClaim;

    @Value("${auth.cognito.email-claim:email}")
    private String emailClaim;

    @Value("${auth.cognito.council-ids-claim:custom:councilIds}")
    private String councilIdsClaim;

    public CognitoAuthProvider(@Value("${auth.cognito.issuer-uri}") String issuerUri) {
        this.jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Override
    public Optional<UserPrincipal> authenticate(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);

            UUID userId = UUID.fromString(jwt.getClaimAsString(userIdClaim));
            String email = jwt.getClaimAsString(emailClaim);

            String rawCouncilIds = jwt.getClaimAsString(councilIdsClaim);
            Set<UUID> councilIds = rawCouncilIds == null ? Set.of() :
                    Arrays.stream(rawCouncilIds.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(UUID::fromString)
                            .collect(Collectors.toUnmodifiableSet());

            return Optional.of(new UserPrincipal(userId, email, councilIds));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
