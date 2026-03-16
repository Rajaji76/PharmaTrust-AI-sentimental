package pharmatrust.manufacturing_system.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger configuration
 * Requirements: NFR-019
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pharmaTrustOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PharmaTrust-AI API")
                        .version("1.0.0")
                        .description("Pharmaceutical Supply Chain Security Platform - " +
                                "Digital certificates, SHA-256 hash-locking, blockchain integration, " +
                                "QR anti-cloning, and AI fraud detection.")
                        .contact(new Contact().name("PharmaTrust Team")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token from /api/v1/auth/login")));
    }
}
