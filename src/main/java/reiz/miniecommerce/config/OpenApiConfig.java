package reiz.miniecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for Swagger UI and for generated clients.
 *
 * <p>The bearer scheme is declared globally because almost every route needs it, and Swagger
 * UI is only useful here if its "Authorize" button actually works: without this the front end
 * developer would have to paste the header by hand on every call.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    public OpenAPI miniEcommerceApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MiniEcommerce API")
                        .version("v1")
                        .description("""
                                Loja com dono único. Autenticação por token Bearer emitido em
                                POST /auth/google a partir de um ID token do Google.

                                Rotas públicas: /auth/google, GET /products/** e o webhook do
                                Mercado Pago, que é público por necessidade e protegido por
                                assinatura HMAC em vez de token.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token devolvido por POST /auth/google")));
    }
}
