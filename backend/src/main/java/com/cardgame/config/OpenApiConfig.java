package com.cardgame.config;

import com.cardgame.common.ErrorResponses;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class OpenApiConfig {

    public static final String SESSION_SCHEME = "sessionCookie";
    public static final String PROBLEM_SCHEMA = "Problem";
    public static final String FIELD_VIOLATION_SCHEMA = "FieldViolation";
    private static final String PROBLEM_JSON = "application/problem+json";

    private static final Map<Integer, String> ERROR_DESCRIPTIONS = Map.of(
            400, "Invalid request (field errors in `errors`)",
            401, "No valid session",
            403, "Missing/invalid CSRF token, or not allowed",
            404, "Not found",
            409, "Conflict with the current state",
            410, "The game was interrupted (server restart) and has no result");

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
                                ProblemDetail (application/problem+json, schema `Problem`) with French title and \
                                detail. Real-time messages: see docs/realtime-protocol.md."""))
                // relative server URL: the generated client works behind the Angular dev proxy and in prod
                .addServersItem(new Server().url("/").description("Same origin"))
                .components(new Components()
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(SecurityConfig.SESSION_COOKIE_NAME))
                        .addSchemas(FIELD_VIOLATION_SCHEMA, fieldViolationSchema())
                        .addSchemas(PROBLEM_SCHEMA, problemSchema()))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME));
    }

    /** Turns {@link ErrorResponses} into documented problem+json responses. */
    @Bean
    OperationCustomizer errorResponsesCustomizer() {
        return (operation, handlerMethod) -> {
            ErrorResponses errors = handlerMethod.getMethodAnnotation(ErrorResponses.class);
            if (errors == null) {
                return operation;
            }
            ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
            for (int status : errors.value()) {
                ApiResponse declared = responses.get(String.valueOf(status));
                String description = declared != null && declared.getDescription() != null
                        && !declared.getDescription().isBlank()
                        ? declared.getDescription()   // an endpoint-specific @ApiResponse wins
                        : ERROR_DESCRIPTIONS.getOrDefault(status, HttpStatus.valueOf(status).getReasonPhrase());
                responses.addApiResponse(String.valueOf(status), new ApiResponse()
                        .description(description)
                        .content(new Content().addMediaType(PROBLEM_JSON, new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)))));
            }
            operation.setResponses(responses);
            return operation;
        };
    }

    /**
     * Response DTOs ({@code *Dto} schemas) are serialised with every property present (nulls included), so every
     * property is marked required; properties declared nullable stay required but may be {@code null}. The generated
     * TypeScript then has {@code field: T} or {@code field: T | null}, never {@code field?: T}.
     */
    @Bean
    OpenApiCustomizer requiredDtoPropertiesCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (name.endsWith("Dto") && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
        };
    }

    private static Schema<?> fieldViolationSchema() {
        return new ObjectSchema()
                .description("One invalid field")
                .addProperty("field", new StringSchema().example("username"))
                .addProperty("message", new StringSchema().example("Le nom d'utilisateur est obligatoire."))
                .required(List.of("field", "message"));
    }

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details. `title` and `detail` are in French and can be shown as is.")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Requête invalide"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("Certains champs sont invalides."))
                .addProperty("instance", new StringSchema().example("/api/auth/register"))
                .addProperty("errors", new ArraySchema()
                        .items(new Schema<>().$ref("#/components/schemas/" + FIELD_VIOLATION_SCHEMA))
                        .description("Only on 400 validation errors"))
                .required(List.of("title", "status"));
    }
}
