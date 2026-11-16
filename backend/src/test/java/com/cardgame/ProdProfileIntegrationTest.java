package com.cardgame;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** The `prod` profile: proxy-aware HTTPS (Railway), secure cookies, no API docs, no dev seed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ProdProfileIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void cookiesAreSecureBehindTheTlsTerminatingProxy() {
        // Railway's proxy forwards plain HTTP with X-Forwarded-Proto: https
        ResponseEntity<String> me = client().get().uri("/api/auth/me")
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "card-game.up.railway.app")
                .retrieve().toEntity(String.class);
        String xsrf = cookie(me.getHeaders().get(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
        assertThat(xsrf).contains("Secure");
        String token = xsrf.substring("XSRF-TOKEN=".length(), xsrf.indexOf(';'));

        ResponseEntity<String> registered = client().post().uri("/api/auth/register")
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "card-game.up.railway.app")
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token).header("X-XSRF-TOKEN", token)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"username\":\"prod_user\",\"password\":\"password123\",\"age\":30}")
                .retrieve().toEntity(String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> login = client().post().uri("/api/auth/login")
                .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "card-game.up.railway.app")
                .header(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token).header("X-XSRF-TOKEN", token)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"username\":\"prod_user\",\"password\":\"password123\"}")
                .retrieve().toEntity(String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cookie(login.getHeaders().get(HttpHeaders.SET_COOKIE), "JSESSIONID"))
                .contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void apiDocsAndSwaggerUiAreDisabled() {
        assertThat(client().get().uri("/v3/api-docs").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(client().get().uri("/swagger-ui.html").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthIsUpAndTheDevSeedIsNotLoaded() {
        assertThat(client().get().uri("/actuator/health").retrieve().toEntity(String.class).getBody())
                .contains("\"UP\"");
        assertThat(environment.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        ResponseEntity<String> alice = client().post().uri("/api/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{\"username\":\"alice\",\"password\":\"password123\"}")
                .retrieve().toEntity(String.class);
        assertThat(alice.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void prodSettingsAreActive() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("5");
    }

    private static String cookie(List<String> setCookies, String name) {
        assertThat(setCookies).as("Set-Cookie headers").isNotNull();
        return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
    }
}
