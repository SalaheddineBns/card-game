package com.cardgame;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/** D13: the `local` profile loads the dev seed (alice, bob, carol / password123). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class DevSeedIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void seededPlayersCanLogInWithTheDevPassword() {
        for (String username : new String[] {"alice", "bob", "carol"}) {
            SpaHttpClient client = new SpaHttpClient(port);
            client.get("/api/auth/me");

            var login = client.post("/api/auth/login", """
                    {"username":"%s","password":"password123"}""".formatted(username));

            assertThat(login.getStatusCode()).as(username).isEqualTo(HttpStatus.OK);
        }
    }
}
