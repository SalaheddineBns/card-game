package com.cardgame;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The Angular app is served from the same origin (test stub in src/test/resources/static): client-side routes
 * get index.html anonymously, assets are served, and server paths keep their 401/404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@Import(TestcontainersConfiguration.class)
class SpaRoutingIntegrationTest {

    @LocalServerPort
    int port;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/lobby", "/auth/login", "/games/12", "/games/12/result", "/profile"})
    void clientRoutesServeIndexHtmlWithoutASession(String path) {
        ResponseEntity<String> response = new SpaHttpClient(port).get(path);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(org.springframework.http.MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody()).contains("data-testid=\"spa-index\"");
    }

    @Test
    void hashedBundlesAreServedWithALongCache() {
        ResponseEntity<String> response = new SpaHttpClient(port).get("/main-TEST1234.js");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("spa test bundle");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("max-age=31536000");
    }

    @Test
    void indexHtmlIsRevalidated() {
        assertThat(new SpaHttpClient(port).get("/lobby").getHeaders().getFirst(HttpHeaders.CACHE_CONTROL))
                .contains("no-cache");
    }

    @Test
    void aMissingFileIs404NotIndexHtml() {
        assertThat(new SpaHttpClient(port).get("/missing-123.js").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void serverPathsAreNotTurnedIntoTheSpa() {
        SpaHttpClient anonymous = new SpaHttpClient(port);
        assertThat(anonymous.get("/api/unknown").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.get("/actuator/env").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.get("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(anonymous.get("/v3/api-docs").getBody()).startsWith("{");

        // logged in: an unknown API path is a 404 problem, not index.html
        SpaHttpClient player = new SpaHttpClient(port);
        player.get("/api/auth/me");
        player.post("/api/auth/register", """
                {"username":"spa_user","password":"password123","age":30}""");
        player.post("/api/auth/login", """
                {"username":"spa_user","password":"password123"}""");
        ResponseEntity<String> unknown = player.get("/api/unknown");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody()).doesNotContain("spa-index");
    }

    @Test
    void anonymousPostsToTheSpaAreNotAllowed() {
        SpaHttpClient anonymous = new SpaHttpClient(port);
        anonymous.get("/");
        assertThat(anonymous.post("/lobby", "{}").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
