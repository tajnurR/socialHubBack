package com.socialhub.socialhubBackend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI / Swagger UI metadata. Available at {@code /swagger-ui.html}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI socialHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SocialHub API")
                        .version("v1")
                        .description("Multi-tenant social media management & analytics platform"))
                // TODO[SSO]: declared so endpoints can reference it once JWT auth is enforced.
                .components(new Components().addSecuritySchemes(
                        "bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
