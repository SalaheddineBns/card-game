package com.cardgame.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardgame.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/** Custom queries and the V1 constraints, against the Flyway schema on real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PlayerRepositoryTest {

    @Autowired
    PlayerRepository repository;

    @Test
    void findsAPlayerByUsernameIgnoringCase() {
        repository.saveAndFlush(new Player("Alice_1", "{bcrypt}x", 30, Gender.FEMALE));

        assertThat(repository.findByUsernameIgnoreCase("alice_1")).get()
                .extracting(Player::getUsername).isEqualTo("Alice_1");
        assertThat(repository.existsByUsernameIgnoreCase("ALICE_1")).isTrue();
        assertThat(repository.findByUsernameIgnoreCase("bob")).isEmpty();
    }

    @Test
    void newPlayerHasZeroStatsAndACreationTimestamp() {
        Player saved = repository.saveAndFlush(new Player("dave", "{bcrypt}x", 30, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getGamesPlayed()).isZero();
        assertThat(saved.getVictories()).isZero();
        assertThat(saved.getAverageScore()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsAUsernameThatDiffersOnlyByCase() {
        repository.saveAndFlush(new Player("erin", "{bcrypt}x", 30, null));

        assertThatThrownBy(() -> repository.saveAndFlush(new Player("ERIN", "{bcrypt}x", 30, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAnInvalidUsername() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Player("no spaces", "{bcrypt}x", 30, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
