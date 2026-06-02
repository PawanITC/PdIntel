package uk.co.pdintel.payment.config;

/**
 * Spring Security filter chain configuration.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Stateless session (STATELESS) — JWT-based REST API, no HttpSession created.</li>
 *   <li>CSRF disabled — stateless JWT APIs are not vulnerable to CSRF.</li>
 *   <li>PUBLIC_PATHS: actuator, swagger, and webhook endpoint are open;
 *       everything else requires authentication.</li>
 *   <li>/api/v1/webhooks/stripe is public here but NOT unprotected — the webhook handler
 *       verifies Stripe-Signature internally. Two layers of protection.</li>
 *   <li>MockJwtFilter injected by constructor — swapping to CognitoJwtFilter in a later
 *       phase requires changing only this class.</li>
 *   <li>anyRequest().authenticated() as the final catch-all — secure by default.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
import uk.co.pdintel.payment.security.MockJwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/webhooks/stripe"
    };

    private final MockJwtFilter mockJwtFilter;

    public SecurityConfig(MockJwtFilter mockJwtFilter) {
        this.mockJwtFilter = mockJwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(mockJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
