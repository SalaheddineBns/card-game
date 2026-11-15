package com.cardgame.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardgame.game.engine.Claim;
import com.cardgame.game.engine.GameEngine;
import com.cardgame.game.engine.GameResult;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.game.engine.GameState;
import com.cardgame.game.engine.PlayerOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Winner / loser determination at the end of a game.
 *
 * <p>legacy: legacy/src/main/java/webSocket/UserStatusWebSocket.java, handleResult lines 136-251
 * <ul>
 *   <li>initial bounds {@code maxScore = Double.MIN_VALUE, minScore = Double.MAX_VALUE}: line 152</li>
 *   <li>winner/loser loop with strict {@code >} and {@code <}: lines 166-178</li>
 *   <li>victory credited with the <em>running</em> winner inside the same loop: line 184</li>
 *   <li>broadcast {winner, loser, winnerScore, loserScore}: lines 231-250</li>
 * </ul>
 * Legacy iterates a Gson-built map whose order is the JSON key order sent by one browser. The new API takes
 * a {@link SequencedMap} in player order, so "first encountered" is deterministic.
 */
@Disabled("pending port: scoring/winner (plan row 13) - implement GameEngine#determineResult / GameEngine#result")
class GameResultParityTest {

    @ParameterizedTest(name = "{0} -> winner {1} ({2}), loser {3} ({4})")
    @CsvSource({
            // scores in player order, winner, winnerScore, loser, loserScore
            "'alice=3;bob=1',          alice, 3, bob,   1",
            "'alice=1;bob=3',          bob,   3, alice, 1",
            "'alice=0;bob=2;carol=5',  carol, 5, alice, 0",
            "'alice=7;bob=2;carol=4',  alice, 7, bob,   2",
            "'alice=2;bob=9;carol=4;dave=1', bob, 9, dave, 1",
            // single player: winner and loser are the same person (legacy behaviour)
            "'solo=4',                 solo,  4, solo,  4",
    })
    void highestScoreWinsAndLowestScoreLoses(String scores, String winner, int winnerScore,
                                             String loser, int loserScore) {
        GameResult result = GameEngine.determineResult(scores(scores));

        assertThat(result.winner()).isEqualTo(winner);
        assertThat(result.winnerScore()).isEqualTo(winnerScore);
        assertThat(result.loser()).isEqualTo(loser);
        assertThat(result.loserScore()).isEqualTo(loserScore);
    }

    @ParameterizedTest(name = "{0} -> victory only for {1}")
    @CsvSource({
            // Cases where the final winner is also the first player encountered with the max score,
            // so legacy and a "fixed" implementation agree.
            "'alice=5;bob=3',         alice",
            "'alice=5;bob=3;carol=1', alice",
    })
    void creditsAVictoryToTheWinnerOnly(String scores, String winner) {
        GameResult result = GameEngine.determineResult(scores(scores));

        assertThat(result.outcomes()).extracting(PlayerOutcome::player)
                .containsExactlyElementsOf(scores(scores).sequencedKeySet());
        assertThat(result.outcomes()).filteredOn(PlayerOutcome::victory)
                .extracting(PlayerOutcome::player).containsExactly(winner);
    }

    // LEGACY-BUG? Ties: legacy uses strict '>' / '<', so the first player encountered wins / loses.
    // Encounter order is the JSON key order of ONE browser's local score map (effectively arbitrary).
    // With player order as the new encounter order this test pins "first in player order wins the tie".
    // When ALL scores are equal (and > 0), the same first player is both winner AND loser.
    @Disabled("pending user decision: Q4 (tie-break rule)")
    @ParameterizedTest
    @CsvSource({
            "'alice=4;bob=4',         alice, alice",
            "'bob=4;alice=4',         bob,   bob",
            "'alice=1;bob=4;carol=4', bob,   alice",
            "'alice=1;bob=4;carol=1', bob,   alice",
    })
    void tieGoesToTheFirstPlayerInOrder(String scores, String winner, String loser) {
        GameResult result = GameEngine.determineResult(scores(scores));

        assertThat(result.winner()).isEqualTo(winner);
        assertThat(result.loser()).isEqualTo(loser);
    }

    // LEGACY-BUG? maxScore starts at Double.MIN_VALUE (the smallest POSITIVE double, 4.9E-324), not
    // -infinity. If every score is 0, nobody is "greater", so winner == null, nobody gets a victory, and the
    // client shows "null(Score: 4.9E-324 )" on the winner page.
    @Disabled("pending user decision: Q3 (all-zero scores)")
    @Test
    void allZeroScoresProduceNoWinnerLikeLegacy() {
        GameResult result = GameEngine.determineResult(scores("alice=0;bob=0"));

        assertThat(result.winner()).isNull();
        assertThat(result.loser()).isEqualTo("alice");
        assertThat(result.outcomes()).noneMatch(PlayerOutcome::victory);
    }

    // LEGACY-BUG? The victory is credited with the RUNNING winner while iterating (line 184), so every
    // player who is the max-so-far when visited gets a victory. Here bob (3) is visited first and is credited
    // a victory, then alice (5) is credited too: two victories for one game.
    @Disabled("pending user decision: Q5 (victory credited to running winner)")
    @Test
    void earlierRunningLeaderIsAlsoCreditedAVictoryLikeLegacy() {
        GameResult result = GameEngine.determineResult(scores("bob=3;alice=5"));

        assertThat(result.winner()).isEqualTo("alice");
        assertThat(result.outcomes()).filteredOn(PlayerOutcome::victory)
                .extracting(PlayerOutcome::player).containsExactly("bob", "alice");
    }

    @Test
    void resultOfAFinishedGameUsesTheServerScores() {
        // given: alice A/RED, bob L/GREEN, center L/RED; alice claims SAME_COLOR correctly, then time runs out
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0)
                .then(11, 1, 3, 1, 11, 2, 3, 1, 2, 0, 2, 1);
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        GameEngine engine = new GameEngine(random, clock);
        GameState state = engine.start(List.of("alice", "bob"), new GameSettings(1, Duration.ofSeconds(10), 0));
        state = engine.claim(state, "alice", Claim.SAME_COLOR).state();
        clock.advance(Duration.ofSeconds(10));
        state = engine.roundTimerExpired(state);

        // when
        GameResult result = engine.result(state);

        // then
        assertThat(result.winner()).isEqualTo("alice");
        assertThat(result.winnerScore()).isEqualTo(1);
        assertThat(result.loser()).isEqualTo("bob");
        assertThat(result.loserScore()).isZero();
    }

    /** Parses "alice=3;bob=1" into an insertion-ordered map. */
    private static SequencedMap<String, Integer> scores(String spec) {
        SequencedMap<String, Integer> map = new LinkedHashMap<>();
        for (String pair : spec.split(";")) {
            String[] kv = pair.split("=");
            map.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
        return map;
    }
}
