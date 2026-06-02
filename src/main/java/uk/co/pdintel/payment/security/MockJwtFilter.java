package uk.co.pdintel.payment.security;

/**
 * Servlet filter that extracts and validates the Bearer token from each HTTP request.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Extends OncePerRequestFilter — guaranteed single execution per request,
 *       no double-execution on forward/include dispatches.</li>
 *   <li>Strips "Bearer " prefix before passing raw token to AuthProvider —
 *       AuthProvider knows nothing about HTTP headers.</li>
 *   <li>Sets UsernamePasswordAuthenticationToken in SecurityContextHolder —
 *       standard Spring Security Authentication implementation.</li>
 *   <li>Always calls chain.doFilter() — filter enriches context only.
 *       SecurityConfig decides which paths require authentication.</li>
 *   <li>Skips if SecurityContext already populated — prevents overwriting
 *       auth set by another filter.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class MockJwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthProvider authProvider;

    public MockJwtFilter(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader("Authorization");

            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length());

                authProvider.authenticate(token).ifPresent(principal -> {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, List.of());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }

        chain.doFilter(request, response);
    }
}
