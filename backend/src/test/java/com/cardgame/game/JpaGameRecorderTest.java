package com.cardgame.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardgame.TestcontainersConfiguration;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.player.Player;
import com.cardgame.player.PlayerMapperImpl;
import com.cardgame.player.PlayerRepository;
import com.cardgame.player.PlayerService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** D6/D22 persistence against the Flyway schema (V1 + V2) on real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaGameRecorder.class, PlayerService.class, PlayerMapperImpl.class})
class JpaGameRecorderTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final GameSettings SETTINGS = new GameSettings(2, Duration.ofSeconds(15));

    @Autowired
    JpaGameRecorder recorder;

    @Autowired
    PlayerRepository players;

    @Autowired
    GameRepository games;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createStoresAnInProgressGame() {
        long id = recorder.createGame(SETTINGS, T0);

        Game game = games.findById(id).orElseThrow();
        assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(game.getStartedAt()).isEqualTo(T0);
        assertThat(game.getFinishedAt()).isNull();
        assertThat(game.getTotalRounds()).isEqualTo(2);
        assertThat(game.getRoundSeconds()).isEqualTo(15);
        assertThat(game.getGameMode()).isEqualTo("Normal");
        assertThat(recorder.findFinished(id)).isEmpty();
    }

    @Test
    void finishingTwoGamesUpdatesStatsEveryTimeAndCreditsOnlyWinners() {
        players.save(new Player("Rec_A", "{bcrypt}x", 20, null));
        players.save(new Player("rec_b", "{bcrypt}x", 20, null));

        // game 1: Rec_A wins 3-1
        long first = recorder.createGame(SETTINGS, T0);
        recorder.finishGame(first, T0.plusSeconds(30), List.of(
                new PlayerGameRecord("Rec_A", 3, true), new PlayerGameRecord("rec_b", 1, false)));
        // game 2: draw 0-0 (D3), records passed with another case for the second player
        long second = recorder.createGame(SETTINGS, T0.plusSeconds(60));
        recorder.finishGame(second, T0.plusSeconds(90), List.of(
                new PlayerGameRecord("REC_B", 0, false), new PlayerGameRecord("Rec_A", 0, false)));
        players.flush();

        Player a = players.findByUsernameIgnoreCase("rec_a").orElseThrow();
        Player b = players.findByUsernameIgnoreCase("rec_b").orElseThrow();
        assertThat(a.getGamesPlayed()).isEqualTo(2);
        assertThat(a.getVictories()).isEqualTo(1);
        assertThat(a.getAverageScore()).isEqualTo(1.5);
        assertThat(b.getGamesPlayed()).isEqualTo(2);
        assertThat(b.getVictories()).isZero();
        assertThat(b.getAverageScore()).isEqualTo(0.5);

        assertThat(jdbc.queryForObject("select count(*) from participations where game_id = ?", Integer.class, first))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from games where id = ?", String.class, second))
                .isEqualTo("FINISHED");

        FinishedGame one = recorder.findFinished(first).orElseThrow();
        assertThat(one.finishedAt()).isEqualTo(T0.plusSeconds(30));
        assertThat(one.records()).containsExactly(
                new PlayerGameRecord("Rec_A", 3, true), new PlayerGameRecord("rec_b", 1, false));
        // player order of game 2 is kept (rec_b first), canonical spelling, nobody wins a draw
        assertThat(recorder.findFinished(second).orElseThrow().records()).containsExactly(
                new PlayerGameRecord("rec_b", 0, false), new PlayerGameRecord("Rec_A", 0, false));
    }

    @Test
    void unknownGameIsNotFinished() {
        assertThat(recorder.findFinished(987_654L)).isEmpty();
    }
}
