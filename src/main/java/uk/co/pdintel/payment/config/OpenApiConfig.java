package uk.co.pdintel.payment.config;

/**
 * OpenAPI 3.x configuration for Plany Payment Service.
 *
 * <p>Decisions:
 * <ul>
 *   <li>Declares a global JWT Bearer security scheme so the Swagger UI Authorize button
 *       is available from day one — ready for Mock auth (Phase 1) and Cognito (later).</li>
 *   <li>@SecurityRequirement is NOT applied globally here — the webhook endpoint must remain
 *       public, so security is declared per-controller via @SecurityRequirement(name="bearerAuth").</li>
 *   <li>Uses springdoc-openapi 2.x (io.swagger.v3.oas.models) — not legacy springfox packages.</li>
 * </ul>
 *
 * @author Pawan
 * @copyright 2026
 */
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {

    @Bean
    @Profile("dev")
    public OpenAPI planyOpenAPIDev() {
        return new OpenAPI()
                .info(new Info()
                        .title("Plany Payment Service API")
                        .description("""
                                Stripe payment integration for Plany.co.uk

                                **Dev auth** — click Authorize and enter: `dev-static-token`

                                **Dev council IDs:**
                                - `123e4567-e89b-12d3-a456-426614174000`
                                - `223e4567-e89b-12d3-a456-426614174001`

                                **Stripe pricing table:** `prctbl_1ThMLMAfNo4hbb7vhJ5NUmcg`
                                """)
                        .version("v1")
                        .contact(new Contact()
                                .name("PdIntel")
                                .url("https://plany.co.uk")));
    }

    @Bean
    @Profile("!dev")
    public OpenAPI planyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Plany Payment Service API")
                        .description("Stripe payment integration for Plany.co.uk — subscription management, webhooks, and billing")
                        .version("v1")
                        .contact(new Contact()
                                .name("PdIntel")
                                .url("https://plany.co.uk")));
    }
}
