package com.cardgame;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * /v3/api-docs builds without a session and lists the auth endpoints. Also writes it to
 * target/openapi.json so the frontend can generate its client without starting the backend.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class OpenApiDocsIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void apiDocsAreAvailableAndDescribeTheAuthEndpoints() throws IOException {
        ResponseEntity<String> docs = new SpaHttpClient(port).get("/v3/api-docs");

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> paths = JsonPath.read(docs.getBody(), "$.paths");
        assertThat(paths).containsKeys("/api/auth/register", "/api/auth/login", "/api/auth/logout", "/api/auth/me");
        assertThat((Object) JsonPath.read(docs.getBody(), "$.components.schemas.PlayerDto")).isNotNull();
        assertThat((Object) JsonPath.read(docs.getBody(), "$.components.schemas.RegisterRequest")).isNotNull();
        java.util.List<String> operationIds = JsonPath.read(docs.getBody(), "$.paths.*.*.operationId");
        assertThat(operationIds).contains("register", "login", "logout", "me", "getMyProfile", "updateMyProfile",
                "getOnlinePlayers", "createInvitation", "acceptInvitation", "declineInvitation",
                "getPendingInvitations", "getCurrentGame", "getGame", "getGameResult");
        // JSON endpoints declare application/json, never */*
        assertThat(docs.getBody()).doesNotContain("\"*/*\"");
        assertThat((Object) JsonPath.read(docs.getBody(),
                "$.paths['/api/auth/login'].post.responses['200'].content['application/json']")).isNotNull();
        // errors use the typed Problem schema with field errors
        assertThat((String) JsonPath.read(docs.getBody(),
                "$.paths['/api/auth/register'].post.responses['400'].content['application/problem+json'].schema.$ref"))
                .isEqualTo("#/components/schemas/Problem");
        assertThat((String) JsonPath.read(docs.getBody(), "$.components.schemas.Problem.properties.errors.items.$ref"))
                .isEqualTo("#/components/schemas/FieldViolation");
        // 204s document no body
        assertThat((Object) JsonPath.read(docs.getBody(), "$.paths['/api/auth/logout'].post.responses['204']"))
                .asString().doesNotContain("content");
        // read DTOs: every property required (nullable ones stay nullable)
        Map<String, Map<String, Object>> schemas = JsonPath.read(docs.getBody(), "$.components.schemas");
        for (String dto : java.util.List.of("GameSnapshotDto", "GameResultDto", "InvitationDto", "OnlinePlayerDto",
                "DealDto", "CardDto", "PlayerCardDto", "PlayerScoreDto", "PlayerDto", "PlayerProfileDto",
                "GameSettingsDto")) {
            Map<String, Object> schema = schemas.get(dto);
            assertThat(schema).as(dto).isNotNull();
            assertThat((java.util.List<String>) schema.get("required")).as(dto)
                    .containsExactlyInAnyOrderElementsOf(((Map<String, Object>) schema.get("properties")).keySet());
        }
        assertThat((java.util.List<String>) JsonPath.read(docs.getBody(), "$.components.schemas.GameSnapshotDto.required"))
                .contains("roundStartedAt", "roundEndsAt", "serverTime");
        assertThat((java.util.List<String>) JsonPath.read(docs.getBody(), "$.components.schemas.InvitationDto.properties.gameId.type"))
                .contains("null");
        // card values are enums (11 symbols without G, lower-case colors)
        assertThat((java.util.List<String>) JsonPath.read(docs.getBody(), "$.components.schemas.CardDto.properties.symbol.enum"))
                .containsExactly("A", "B", "C", "D", "E", "F", "H", "I", "J", "K", "L");
        assertThat((java.util.List<String>) JsonPath.read(docs.getBody(), "$.components.schemas.CardDto.properties.color.enum"))
                .containsExactly("red", "blue", "green");
        assertThat((java.util.List<String>) JsonPath.read(docs.getBody(), "$.components.schemas.PlayerCardDto.properties.color.enum"))
                .containsExactly("red", "blue", "green");
        assertThat((String) JsonPath.read(docs.getBody(), "$.servers[0].url")).isEqualTo("/");

        Files.writeString(Path.of("target", "openapi.json"), docs.getBody());
    }

    @Test
    void healthEndpointIsPublic() {
        assertThat(new SpaHttpClient(port).get("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
