package com.cardgame.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SESSION_SCHEME = "sessionCookie";

    @Bean
    OpenAPI cardGameOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Card Game API")
                        .version("v1")
                        .description("""
                                REST API of the card game. Authentication uses the JSESSIONID session cookie \
                                set by POST /api/auth/login. Every POST/PUT/PATCH/DELETE must send the value of \
                                the XSRF-TOKEN cookie in the X-XSRF-TOKEN header. Errors are RFC 9457 \
                                ProblemDetail (application/problem+json). Real-time messages: see \
                                docs/realtime-protocol.md."""))
                // relative server URL: the generated client works behind the Angular dev proxy and in prod
                .addServersItem(new Server().url("/").description("Same origin"))
                .components(new Components().addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(SecurityConfig.SESSION_COOKIE_NAME)))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }
}
