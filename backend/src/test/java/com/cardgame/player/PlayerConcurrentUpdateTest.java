package com.cardgame.player;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardgame.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G3: a profile edit that overlaps with the end of a game must not write back stale stats (B21 as a race).
 * Two real transactions: the profile edit loads the player, the game end commits new stats, then the edit commits.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, PlayerService.class, PlayerMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlayerConcurrentUpdateTest {

    @Autowired
    PlayerRepository players;

    @Autowired
    PlayerService playerService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void profileEditDoesNotOverwriteStatsCommittedMeanwhile() {
        // given
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        TransactionTemplate otherTx = new TransactionTemplate(transactionManager);
        otherTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(s -> players.save(new Player("race_p", "{bcrypt}x", 20, null)));

        // when: the edit has loaded the player (stats 0/0/0.0) ...
        tx.executeWithoutResult(s -> {
            Player stale = players.findByUsernameIgnoreCase("race_p").orElseThrow();
            stale.setAge(41);
            // ... the end of a game commits in its own transaction ...
            otherTx.executeWithoutResult(inner -> playerService.recordGameResult("race_p", 5, true));
            // ... and only then the edit commits
        });

        // then: both changes survive
        Player saved = tx.execute(s -> players.findByUsernameIgnoreCase("race_p").orElseThrow());
        assertThat(saved.getAge()).isEqualTo(41);
        assertThat(saved.getGamesPlayed()).isEqualTo(1);
        assertThat(saved.getVictories()).isEqualTo(1);
        assertThat(saved.getAverageScore()).isEqualTo(5.0);
    }
}
