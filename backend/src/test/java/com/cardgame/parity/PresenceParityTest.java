package com.cardgame.parity;

import static com.cardgame.realtime.Destinations.PRESENCE_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import com.cardgame.presence.PlayerStatus;
import com.cardgame.presence.PresenceService;
import com.cardgame.presence.dto.OnlinePlayerDto;
import com.cardgame.presence.dto.PlayerOffline;
import com.cardgame.presence.dto.PlayerOnline;
import com.cardgame.presence.dto.PlayerStatusChanged;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Online presence in the lobby (plan row 7). Contract: docs/migration-plan.md, "Contract for backend:
 * invitation and game services" → Presence.
 *
 * <p>legacy: UserStatusWebSocket#broadcast lines 435-449 ({@code login}/{@code logout} to every socket),
 * called from LoginServlet line 100 and LogoutServlet line 28; home.jsp sidebar lines 17-34 (current user
 * excluded) and handler lines 126-153.
 */
@Disabled("pending port: presence (plan row 7) - implement com.cardgame.presence.PresenceService")
class PresenceParityTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private RecordingPublisher publisher;
    private PresenceService presence;

    @BeforeEach
    void setUp() {
        publisher = new RecordingPublisher();
        presence = new PresenceService(new MutableClock(T0), publisher);
    }

    @Test
    void firstConnectionAnnouncesThePlayerOnline() {
        presence.connected("alice", "s1");

        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerOnline.class))
                .containsExactly(new PlayerOnline("alice", PlayerStatus.ONLINE, T0));
    }

    // DECISION Q15: several tabs are allowed; the player is offline only when the last tab disconnects.
    // DECISION (B17): legacy never reported a closed socket and kept expired sessions in the list.
    @Test
    void playerStaysOnlineUntilTheLastTabDisconnects() {
        presence.connected("alice", "s1");
        presence.connected("alice", "s2");
        presence.disconnected("alice", "s1");

        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerOnline.class)).hasSize(1);
        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerOffline.class)).isEmpty();
        assertThat(presence.find("alice")).isPresent();

        presence.disconnected("alice", "s2");

        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerOffline.class))
                .containsExactly(new PlayerOffline("alice", T0));
        assertThat(presence.find("alice")).isEmpty();
    }

    @Test
    void unknownSessionsAreIgnoredOnDisconnect() {
        presence.connected("alice", "s1");
        publisher.clear();

        presence.disconnected("alice", "nope");
        presence.disconnected("bob", "s9");

        assertThat(publisher.all()).isEmpty();
        assertThat(presence.find("alice")).isPresent();
    }

    @Test
    void onlineListExcludesTheCallerAndIsSortedIgnoringCase() {
        presence.connected("carol", "s1");
        presence.connected("Bob", "s2");
        presence.connected("alice", "s3");

        assertThat(presence.onlinePlayers("ALICE")).containsExactly(
                new OnlinePlayerDto("Bob", PlayerStatus.ONLINE),
                new OnlinePlayerDto("carol", PlayerStatus.ONLINE));
    }

    @Test
    void findIgnoresCaseAndReturnsTheRegisteredSpelling() {
        presence.connected("Bob", "s1");

        assertThat(presence.find("bOB")).contains(new OnlinePlayerDto("Bob", PlayerStatus.ONLINE));
        assertThat(presence.find("zoe")).isEmpty();
    }

    // DECISION Q16 / Q17: players in a game are shown IN_GAME, also after a reconnect during the game.
    @Test
    void inGameStatusIsAnnouncedAndSurvivesAReconnect() {
        presence.connected("alice", "s1");
        presence.connected("bob", "s2");
        publisher.clear();

        presence.setInGame(List.of("alice", "bob"), true);

        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerStatusChanged.class)).containsExactlyInAnyOrder(
                new PlayerStatusChanged("alice", PlayerStatus.IN_GAME, T0),
                new PlayerStatusChanged("bob", PlayerStatus.IN_GAME, T0));
        assertThat(presence.find("alice")).contains(new OnlinePlayerDto("alice", PlayerStatus.IN_GAME));

        // bob drops and comes back during the game
        presence.disconnected("bob", "s2");
        publisher.clear();
        presence.connected("bob", "s3");
        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerOnline.class))
                .containsExactly(new PlayerOnline("bob", PlayerStatus.IN_GAME, T0));

        // game over
        publisher.clear();
        presence.setInGame(List.of("alice", "bob"), false);
        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerStatusChanged.class)).containsExactlyInAnyOrder(
                new PlayerStatusChanged("alice", PlayerStatus.ONLINE, T0),
                new PlayerStatusChanged("bob", PlayerStatus.ONLINE, T0));
    }

    @Test
    void statusChangesOfOfflinePlayersOrWithoutChangeAreNotAnnounced() {
        presence.connected("alice", "s1");
        presence.setInGame(List.of("alice"), true);
        publisher.clear();

        presence.setInGame(List.of("alice"), true);   // no change
        presence.setInGame(List.of("zoe"), true);     // offline

        assertThat(publisher.all()).isEmpty();
    }
}
