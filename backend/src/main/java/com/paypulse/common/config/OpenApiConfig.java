package com.paypulse.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI config — Swagger UI at /swagger-ui.html, spec at /api-docs.
 * Owner: M4.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayPulse — Payments Processing API")
                        .description("REST API for managing the full lifecycle of financial payments. See docs/openapi.yaml for the frozen contract.")
                        .version("1.1.0"))
                .servers(List.of(
                        new Server().url("/").description("Current server"),
                        new Server().url("http://localhost:8080").description("Local backend direct")
                ));
    }
}

