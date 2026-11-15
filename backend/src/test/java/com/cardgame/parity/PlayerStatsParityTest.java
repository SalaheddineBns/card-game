package com.cardgame.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.cardgame.game.engine.PlayerStats;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Player statistics updated at the end of every game.
 *
 * <p>legacy:
 * <ul>
 *   <li>initial values at registration (0 / 0 / 0.0): legacy/src/main/java/controller/RegisterServlet.java lines 42-46</li>
 *   <li>update: legacy/src/main/java/webSocket/UserStatusWebSocket.java, updatePlayerStats lines 253-262</li>
 *   <li>participation score = Math.round(score): same file, line 222</li>
 * </ul>
 * Legacy NPEs if gamesPlayed or averageScore is null (line 256); new code uses non-null primitives.
 * Click ratios ({@code ratioClicRéussi}, {@code ratioClicRapide}) are never updated by legacy and stay 0.0.
 */
@Disabled("pending port: scoring/winner (plan row 13) - implement com.cardgame.game.engine.PlayerStats")
class PlayerStatsParityTest {

    @Test
    void newPlayerStartsWithZeroStats() {
        PlayerStats stats = PlayerStats.initial();

        assertThat(stats.gamesPlayed()).isZero();
        assertThat(stats.victories()).isZero();
        assertThat(stats.averageScore()).isZero();
    }

    @ParameterizedTest(name = "({0} games, {1} wins, avg {2}) + score {3}, victory={4} -> ({5}, {6}, {7})")
    @CsvSource({
            // gamesPlayed, victories, averageScore, score, victory -> gamesPlayed, victories, averageScore
            "0, 0, 0.0,  4, true,  1, 1, 4.0",
            "1, 1, 4.0,  2, false, 2, 1, 3.0",
            "3, 0, 2.0,  6, false, 4, 0, 3.0",
            "2, 1, 1.5,  0, true,  3, 2, 1.0",
            "4, 2, 2.5,  5, false, 5, 2, 3.0",
            "2, 0, 1.0,  2, false, 3, 0, 1.3333333333",
    })
    void incrementsGamesPlayedUpdatesTheRunningAverageAndCountsVictories(
            int gamesPlayed, int victories, double averageScore, int score, boolean victory,
            int expectedGames, int expectedVictories, double expectedAverage) {
        PlayerStats before = new PlayerStats(gamesPlayed, victories, averageScore);

        PlayerStats after = before.afterGame(score, victory);

        assertThat(after.gamesPlayed()).isEqualTo(expectedGames);
        assertThat(after.victories()).isEqualTo(expectedVictories);
        assertThat(after.averageScore()).isCloseTo(expectedAverage, within(1e-9));
    }
}
