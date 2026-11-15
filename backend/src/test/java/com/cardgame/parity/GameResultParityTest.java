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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Winner / loser / draw determination at the end of a game.
 *
 * <p>legacy: legacy/src/main/java/webSocket/UserStatusWebSocket.java, handleResult lines 136-251
 * <ul>
 *   <li>initial bounds {@code maxScore = Double.MIN_VALUE, minScore = Double.MAX_VALUE}: line 152</li>
 *   <li>winner/loser loop with strict {@code >} and {@code <}: lines 166-178</li>
 *   <li>victory credited with the <em>running</em> winner inside the same loop: line 184</li>
 *   <li>broadcast {winner, loser, winnerScore, loserScore}: lines 231-250</li>
 * </ul>
 * Decisions (docs/migration-plan.md): Q3 all-zero is a draw, Q4 tied leaders share a draw and a player is
 * never both winner and loser, Q5 only the final winner gets a victory.
 *
 * <p>Rule: leaders = players with the top score, trailers = players with the bottom score (empty when
 * everybody has the same score). Winner = the single leader, else none (draw). Loser = the single trailer,
 * else none. Lists are in player order.
 */
@Disabled("pending port: scoring/winner (plan row 13) - implement GameEngine#determineResult / GameEngine#result")
class GameResultParityTest {

    @ParameterizedTest(name = "{0} -> winner {1} ({2}), loser {3} ({4})")
    @CsvSource({
            // scores in player order, winner, topScore, loser, bottomScore  (same as legacy)
            "'alice=3;bob=1',                 alice, 3, bob,   1",
            "'alice=1;bob=3',                 bob,   3, alice, 1",
            "'alice=0;bob=2;carol=5',         carol, 5, alice, 0",
            "'alice=7;bob=2;carol=4',         alice, 7, bob,   2",
            "'alice=2;bob=9;carol=4;dave=1',  bob,   9, dave,  1",
    })
    void highestScoreWinsAndLowestScoreLoses(String scores, String winner, int topScore,
                                             String loser, int bottomScore) {
        GameResult result = GameEngine.determineResult(scores(scores));

        assertThat(result.isDraw()).isFalse();
        assertThat(result.winner()).isEqualTo(winner);
        assertThat(result.leaders()).containsExactly(winner);
        assertThat(result.topScore()).isEqualTo(topScore);
        assertThat(result.loser()).isEqualTo(loser);
        assertThat(result.trailers()).containsExactly(loser);
        assertThat(result.bottomScore()).isEqualTo(bottomScore);
    }

    // DECISION Q5: legacy credited a victory to every player who was the running leader when visited
    // (e.g. 'bob=3;alice=5' credited bob AND alice). Now only the final winner is credited.
    @ParameterizedTest(name = "{0} -> victory only for {1}")
    @CsvSource({
            "'alice=5;bob=3',         alice",
            "'alice=5;bob=3;carol=1', alice",
            "'bob=3;alice=5',         alice",
            "'alice=0;bob=2;carol=5', carol",
            "'alice=1;bob=2;carol=3;dave=4', dave",
    })
    void creditsAVictoryToTheFinalWinnerOnly(String scores, String winner) {
        GameResult result = GameEngine.determineResult(scores(scores));

        assertThat(result.outcomes()).extracting(PlayerOutcome::player)
                .containsExactlyElementsOf(scores(scores).sequencedKeySet());
        assertThat(result.outcomes()).extracting(PlayerOutcome::score)
                .containsExactlyElementsOf(scores(scores).sequencedValues());
        assertThat(result.outcomes()).filteredOn(PlayerOutcome::victory)
                .extracting(PlayerOutcome::player).containsExactly(winner);
    }

    // DECISION Q3: legacy produced winner = null and showed "null(Score: 4.9E-324 )" when all scores were 0.
    // DECISION Q4: legacy broke ties by map iteration order and, when all scores were equal, made the same
    // player winner AND loser. Now tied leaders share a draw (no winner, no victory); tied trailers mean no
    // loser; when everybody is equal there is no trailer at all.
    @ParameterizedTest(name = "{0} -> leaders [{1}] ({2}), trailers [{3}] ({4}), winner {5}, loser {6}")
    @CsvSource({
            // scores, leaders, topScore, trailers, bottomScore, winner (empty = none), loser (empty = none)
            "'alice=0;bob=0',                 alice|bob,       0, ,           0, ,      ",
            "'alice=0;bob=0;carol=0',         alice|bob|carol, 0, ,           0, ,      ",
            "'alice=4;bob=4',                 alice|bob,       4, ,           4, ,      ",
            "'bob=4;alice=4',                 bob|alice,       4, ,           4, ,      ",
            "'alice=1;bob=4;carol=4',         bob|carol,       4, alice,      1, ,      alice",
            "'alice=5;bob=1;carol=1',         alice,           5, bob|carol,  1, alice, ",
            "'alice=4;bob=4;carol=1;dave=1',  alice|bob,       4, carol|dave, 1, ,      ",
    })
    void tiesAndAllZeroScoresAreDraws(String scores, String leaders, int topScore, String trailers,
                                      int bottomScore, String winner, String loser) {
        GameResult result = GameEngine.determineResult(scores(scores));

        assertThat(result.leaders()).containsExactlyElementsOf(names(leaders));
        assertThat(result.topScore()).isEqualTo(topScore);
        assertThat(result.trailers()).containsExactlyElementsOf(names(trailers));
        assertThat(result.bottomScore()).isEqualTo(bottomScore);
        assertThat(result.winner()).isEqualTo(winner);
        assertThat(result.loser()).isEqualTo(loser);
        assertThat(result.isDraw()).isEqualTo(winner == null);
        assertThat(result.outcomes()).filteredOn(PlayerOutcome::victory)
                .extracting(PlayerOutcome::player)
                .containsExactlyElementsOf(winner == null ? List.of() : List.of(winner));
    }

    @Test
    void resultOfAFinishedGameUsesTheServerScores() {
        // given: alice A/RED, bob L/GREEN, center L/RED; alice claims SAME_COLOR correctly, then time runs out
        ScriptedRandom random = ScriptedRandom.of(11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0)
                .then(11, 1, 3, 1, 11, 2, 3, 1, 2, 0, 2, 1);
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        GameEngine engine = new GameEngine(random, clock);
        GameState state = engine.start(List.of("alice", "bob"), new GameSettings(1, Duration.ofSeconds(10)));
        state = engine.claim(state, "alice", Claim.SAME_COLOR, state.dealVersion()).state();
        clock.advance(Duration.ofSeconds(10));
        state = engine.roundTimerExpired(state);

        // when
        GameResult result = engine.result(state);

        // then
        assertThat(result.winner()).isEqualTo("alice");
        assertThat(result.topScore()).isEqualTo(1);
        assertThat(result.loser()).isEqualTo("bob");
        assertThat(result.bottomScore()).isZero();
    }

    @Test
    void finishedGameWithoutAnyPointIsADraw() {
        // DECISION Q3
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        GameEngine engine = new GameEngine(new java.util.Random(3), clock);
        GameState state = engine.start(List.of("alice", "bob"), new GameSettings(1, Duration.ofSeconds(10)));
        state = engine.roundTimerExpired(state);

        GameResult result = engine.result(state);

        assertThat(result.isDraw()).isTrue();
        assertThat(result.winner()).isNull();
        assertThat(result.loser()).isNull();
        assertThat(result.outcomes()).noneMatch(PlayerOutcome::victory);
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

    /** Parses "alice|bob" (null or blank = empty). */
    private static List<String> names(String spec) {
        return spec == null || spec.isBlank() ? List.of() : Arrays.stream(spec.split("\\|")).map(String::trim).toList();
    }
}
