package com.cardgame.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardgame.TestcontainersConfiguration;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.player.Player;
import com.cardgame.player.PlayerMapperImpl;
import com.cardgame.player.PlayerRepository;
import com.cardgame.player.PlayerService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** D25 against the Flyway schema (V1-V3) on real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaGameRecorder.class, PlayerService.class, PlayerMapperImpl.class})
class AbandonedGameCleanupTest {

    private static final Instant BOOT = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    JpaGameRecorder recorder;

    @Autowired
    PlayerRepository players;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void abandonsOnlyGamesLeftInProgressBeforeStartup() {
        // given: a player with stats from a finished game, an in-progress game from before the restart,
        // and one started after boot
        players.save(new Player("cleanup_p", "{bcrypt}x", 20, null));
        long finished = recorder.createGame(GameSettings.defaults(), BOOT.minusSeconds(600));
        recorder.finishGame(finished, BOOT.minusSeconds(570), List.of(new PlayerGameRecord("cleanup_p", 2, true)));
        long stale = recorder.createGame(GameSettings.defaults(), BOOT.minusSeconds(30));
        long fresh = recorder.createGame(GameSettings.defaults(), BOOT.plusSeconds(1));
        players.flush();
        int participationsBefore = count("select count(*) from participations");

        // when
        AbandonedGameCleanup cleanup = new AbandonedGameCleanup(recorder, Clock.fixed(BOOT, ZoneOffset.UTC));
        cleanup.abandonGamesFromBeforeStartup();

        // then
        assertThat(recorder.findStatus(stale)).contains(GameStatus.ABANDONED);
        assertThat(jdbc.queryForObject("select finished_at from games where id = ?", java.sql.Timestamp.class, stale)
                .toInstant()).isEqualTo(BOOT);
        assertThat(recorder.findStatus(fresh)).contains(GameStatus.IN_PROGRESS);
        assertThat(recorder.findStatus(finished)).contains(GameStatus.FINISHED);
        assertThat(recorder.findFinished(finished)).isPresent();
        assertThat(count("select count(*) from participations")).isEqualTo(participationsBefore);
        Player p = players.findByUsernameIgnoreCase("cleanup_p").orElseThrow();
        assertThat(p.getGamesPlayed()).isEqualTo(1);
        assertThat(p.getVictories()).isEqualTo(1);
    }

    @Test
    void abandonGameMarksOnlyAnInProgressGame() {
        long game = recorder.createGame(GameSettings.defaults(), BOOT);

        recorder.abandonGame(game, BOOT.plusSeconds(30));
        recorder.abandonGame(game, BOOT.plusSeconds(60));   // no-op the second time

        assertThat(recorder.findStatus(game)).contains(GameStatus.ABANDONED);
        assertThat(recorder.findStatus(123_456L)).isEmpty();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
