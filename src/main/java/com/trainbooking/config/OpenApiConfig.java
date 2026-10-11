package com.trainbooking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger documentation configuration with JWT bearer authentication support.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    /**
     * Creates and configures the OpenAPI metadata and security schemes.
     *
     * @return customized {@link OpenAPI} specification bean
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Train Scheduling & Booking System API")
                        .version("1.0.0")
                        .description("SLIIT Software Engineering Project (SE2030) - 2026-Y2-S1-MLB-B9G2-10\n" +
                                "RESTful API documentation for train scheduling, seat reservations, payments, and notifications.")
                        .contact(new Contact()
                                .name("SLIIT SE Development Team")
                                .email("support@trainbooking.lk"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token to authorize requests.")));
    }
}
