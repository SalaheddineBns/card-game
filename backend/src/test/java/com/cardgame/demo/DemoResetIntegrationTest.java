package com.cardgame.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardgame.SpaHttpClient;
import com.cardgame.TestcontainersConfiguration;
import com.cardgame.game.GameService;
import com.cardgame.game.GameSessionRegistry;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.invitation.InvitationService;
import com.cardgame.invitation.dto.CreateInvitationRequest;
import com.cardgame.presence.PresenceService;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D29: reset() logs everybody out, clears live games, invitations and presence, empties the tables (ids restart)
 * and recreates the demo players. Own context (distinct demo password) so the truncation never touches the
 * database shared by the other integration tests. The cron job itself stays disabled here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.servlet.session.cookie.secure=false",
        "cardgame.demo-reset.demo-password=reset-test-pass"})
@Import(TestcontainersConfiguration.class)
class DemoResetIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    DemoResetService demoReset;

    @Autowired
    GameService games;

    @Autowired
    GameSessionRegistry liveGames;

    @Autowired
    InvitationService invitations;

    @Autowired
    PresenceService presence;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void resetLeavesOnlyFreshDemoPlayersAndLogsEverybodyOut() {
        // given: two real accounts, one logged in, a finished-or-live game, a pending invitation, presence
        SpaHttpClient zed = loggedIn("reset_zed");
        loggedIn("reset_yan");
        loggedIn("reset_xia");
        presence.connected("reset_zed", "s-zed");
        presence.connected("reset_yan", "s-yan");
        presence.connected("reset_xia", "s-xia");
        long gameId = games.startGame(List.of("reset_zed", "reset_yan"), new GameSettings(3, Duration.ofSeconds(120)));
        presence.connected("reset_wil", "s-wil");
        invitations.create("reset_xia", new CreateInvitationRequest(List.of("reset_wil"), null));
        assertThat(zed.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveGames.isLive(gameId)).isTrue();

        // when
        DemoResetService.Result result = demoReset.reset();

        // then: everybody is logged out
        assertThat(zed.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.httpSessions()).isGreaterThanOrEqualTo(3);
        // in-memory state is gone (timers cancelled with the games)
        assertThat(liveGames.isLive(gameId)).isFalse();
        assertThat(games.currentGameOf("reset_zed")).isEmpty();
        assertThat(invitations.pendingFor("reset_xia")).isEmpty();
        assertThat(presence.onlinePlayers(null)).isEmpty();
        assertThat(presence.isInGame("reset_zed")).isFalse();
        assertThat(result.liveGames()).isEqualTo(1);
        assertThat(result.pendingInvitations()).isEqualTo(1);
        // the tables hold only the demo players, ids restarted, zero stats
        assertThat(count("games")).isZero();
        assertThat(count("participations")).isZero();
        assertThat(jdbc.queryForList("select id, username, games_played, victories, average_score from players order by id"))
                .extracting(row -> List.of(row.get("id"), row.get("username"), row.get("games_played"),
                        row.get("victories"), row.get("average_score")))
                .containsExactly(
                        List.of(1L, "alice", 0, 0, 0.0),
                        List.of(2L, "bob", 0, 0, 0.0),
                        List.of(3L, "carol", 0, 0, 0.0));
        assertThat(result.demoPlayers()).isEqualTo(3);
        assertThat(result.deletedPlayers()).isGreaterThanOrEqualTo(3);   // zed, yan, xia (wil is only "online")
        // and the demo players can log in with the demo password (bcrypt)
        assertThat(jdbc.queryForObject("select password_hash from players where username = 'bob'", String.class))
                .startsWith("{bcrypt}");
        for (String demo : List.of("alice", "bob", "carol")) {
            SpaHttpClient client = new SpaHttpClient(port);
            client.get("/api/auth/me");
            ResponseEntity<String> login = client.post("/api/auth/login",
                    "{\"username\":\"%s\",\"password\":\"reset-test-pass\"}".formatted(demo));
            assertThat(login.getStatusCode()).as(demo).isEqualTo(HttpStatus.OK);
            assertThat((Integer) JsonPath.read(client.get("/api/players/me").getBody(), "$.gamesPlayed")).isZero();
        }
        // a game created after the reset gets id 1 again
        assertThat(games.startGame(List.of("alice", "bob"), GameSettings.defaults())).isEqualTo(1L);
        games.discardLiveGames();
    }

    @Test
    void seedIfEmptyOnlyCreatesTheDemoPlayersWhenThereAreNoPlayers() {
        demoReset.reset();
        assertThat(demoReset.seedIfEmpty()).isZero();             // demo players already there
        jdbc.execute("truncate table participations, games, players restart identity cascade");

        assertThat(demoReset.seedIfEmpty()).isEqualTo(3);
        assertThat(count("players")).isEqualTo(3);
    }

    private SpaHttpClient loggedIn(String username) {
        SpaHttpClient client = new SpaHttpClient(port);
        client.get("/api/auth/me");
        client.post("/api/auth/register", """
                {"username":"%s","password":"password123","age":30}""".formatted(username));
        assertThat(client.post("/api/auth/login", """
                {"username":"%s","password":"password123"}""".formatted(username)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return client;
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
