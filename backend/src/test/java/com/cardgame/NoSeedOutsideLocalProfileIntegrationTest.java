package com.cardgame;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardgame.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The dev seed must not be applied without the `local` profile (D13). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@Import(TestcontainersConfiguration.class)
class NoSeedOutsideLocalProfileIntegrationTest {

    @Autowired
    PlayerRepository players;

    @Test
    void seedAccountsAreAbsent() {
        assertThat(players.findByUsernameIgnoreCase("carol")).isEmpty();
    }
}
