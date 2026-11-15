package com.cardgame.parity;

import static com.cardgame.realtime.Destinations.ERRORS_QUEUE;
import static com.cardgame.realtime.Destinations.GAMES_QUEUE;
import static com.cardgame.realtime.Destinations.PRESENCE_TOPIC;
import static com.cardgame.realtime.Destinations.gameTopic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardgame.common.DomainException;
import com.cardgame.game.GameFinishedException;
import com.cardgame.game.GameInProgressException;
import com.cardgame.game.GameNotFoundException;
import com.cardgame.game.GameService;
import com.cardgame.game.GameSessionRegistry;
import com.cardgame.game.NotAGamePlayerException;
import com.cardgame.game.PlayerGameRecord;
import com.cardgame.game.dto.CardDto;
import com.cardgame.game.dto.ClaimRejected;
import com.cardgame.game.dto.DealDto;
import com.cardgame.game.dto.GameFinished;
import com.cardgame.game.dto.GameResultDto;
import com.cardgame.game.dto.GameSettingsDto;
import com.cardgame.game.dto.GameSnapshotDto;
import com.cardgame.game.dto.GameStarted;
import com.cardgame.game.dto.PlayerCardDto;
import com.cardgame.game.dto.PlayerScoreDto;
import com.cardgame.game.dto.RoundStarted;
import com.cardgame.game.dto.ScoresUpdated;
import com.cardgame.game.engine.Claim;
import com.cardgame.game.engine.ClaimStatus;
import com.cardgame.game.engine.GameEngine;
import com.cardgame.game.engine.GamePhase;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.invitation.InvitationService;
import com.cardgame.invitation.InvitationStatus;
import com.cardgame.invitation.PlayerUnavailableException;
import com.cardgame.invitation.dto.CreateInvitationRequest;
import com.cardgame.invitation.dto.InvitationDto;
import com.cardgame.presence.PlayerStatus;
import com.cardgame.presence.PresenceService;
import com.cardgame.presence.dto.OnlinePlayerDto;
import com.cardgame.presence.dto.PlayerOnline;
import com.cardgame.presence.dto.PlayerStatusChanged;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Live game session: start, claims, server-driven rounds, end of game, persistence, rejoin
 * (plan rows 10-14). Contract: docs/migration-plan.md, "Contract for backend: invitation and game
 * services" → Games.
 *
 * <p>legacy:
 * <ul>
 *   <li>start: UserStatusWebSocket#beginGame lines 379-432 (redirect sent to the players only)</li>
 *   <li>score updates: #handleUpdateScore / #broadcastUpdatedScores lines 323-342 (sent to ALL sockets)</li>
 *   <li>rounds: game.jsp startTimer lines 189-207, #handleFinTour lines 264-276 (sent to ALL sockets)</li>
 *   <li>end: #handleResult lines 136-251 (game row, participations, stats; redirect sent to ALL sockets)</li>
 * </ul>
 * Rules themselves are pinned by the engine parity tests; this class pins the orchestration.
 */
class GameSessionParityTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    // Scripted deals for players [alice, bob] (see CardGenerationParityTest for the draw order)
    /** alice A/RED, bob L/GREEN, center L/RED: alice SAME_COLOR is correct, alice SAME_SYMBOL is wrong. */
    private static final int[] DEAL_1 = {11, 0, 3, 0, 11, 10, 3, 2, 2, 1, 2, 0};
    /** alice B/BLUE, bob C/BLUE, center B/BLUE. */
    private static final int[] DEAL_2 = {11, 1, 3, 1, 11, 2, 3, 1, 2, 0, 2, 1};
    /** alice D/RED, bob E/GREEN, center D/RED. */
    private static final int[] DEAL_3 = {11, 3, 3, 0, 11, 4, 3, 2, 2, 0, 2, 0};

    private MutableClock clock;
    private FakeTaskScheduler scheduler;
    private RecordingPublisher publisher;
    private PresenceService presence;
    private FakeGameRecorder recorder;
    private GameService games;

    private void setUp(RandomGenerator random) {
        clock = new MutableClock(T0);
        scheduler = new FakeTaskScheduler(clock);
        publisher = new RecordingPublisher();
        presence = new PresenceService(clock, publisher);
        recorder = new FakeGameRecorder();
        games = new GameService(new GameEngine(random, clock), new GameSessionRegistry(), recorder,
                presence, publisher, scheduler, clock);
        for (String name : List.of("alice", "bob", "carol", "dave")) {
            presence.connected(name, name + "-tab1");
        }
        publisher.clear();
    }

    private long startAliceAndBob(int totalRounds) {
        return games.startGame(List.of("alice", "bob"), new GameSettings(totalRounds, Duration.ofSeconds(10)));
    }

    // DECISION D22: the game row is written when the game starts (legacy wrote it only at the end, and
    // always the same row: B7), so the id exists for /topic/game/{id} and for rejoining.
    @Test
    void startStoresAnInProgressGameFirstThenNotifiesEachPlayer() {
        setUp(new Random(1));

        long gameId = startAliceAndBob(3);

        assertThat(gameId).isEqualTo(1L);
        assertThat(recorder.calls()).containsExactly("createGame:1");
        FakeGameRecorder.Row row = recorder.row(gameId);
        assertThat(row.status).isEqualTo(FakeGameRecorder.Status.IN_PROGRESS);
        assertThat(row.startedAt).isEqualTo(T0);
        assertThat(row.settings).isEqualTo(new GameSettings(3, Duration.ofSeconds(10)));

        assertThat(publisher.recipientsOf(GAMES_QUEUE, GameStarted.class)).containsExactlyInAnyOrder("alice", "bob");
        assertThat(publisher.toUser("alice", GAMES_QUEUE, GameStarted.class))
                .containsExactly(new GameStarted(gameId, T0));
        assertThat(publisher.toUser("carol", GAMES_QUEUE, GameStarted.class)).isEmpty();

        // DECISION Q16: the players are shown "in game" in the lobby
        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerStatusChanged.class)).containsExactlyInAnyOrder(
                new PlayerStatusChanged("alice", PlayerStatus.IN_GAME, T0),
                new PlayerStatusChanged("bob", PlayerStatus.IN_GAME, T0));
        assertThat(scheduler.pendingTaskTimes()).containsExactly(T0.plusSeconds(10));
        assertThat(games.isPlayer(gameId, "Alice")).isTrue();
        assertThat(games.isPlayer(gameId, "carol")).isFalse();
        assertThat(games.isPlayer(999L, "alice")).isFalse();
    }

    // DECISION Q14: legacy sent scoreUpdate to every open socket (B15). Now only the game topic.
    @Test
    void correctClaimIsBroadcastOnTheGameTopicOnly() {
        setUp(ScriptedRandom.of(DEAL_1).then(DEAL_2));
        long gameId = startAliceAndBob(3);
        publisher.clear();

        games.claim(gameId, "ALICE", Claim.SAME_COLOR, 1);

        assertThat(publisher.onTopic(gameTopic(gameId), ScoresUpdated.class)).containsExactly(new ScoresUpdated(
                gameId, "alice",
                List.of(new PlayerScoreDto("alice", 1), new PlayerScoreDto("bob", 0)),
                new DealDto(2,
                        List.of(new PlayerCardDto("alice", "B", "blue"), new PlayerCardDto("bob", "C", "blue")),
                        new CardDto("B", "blue")),
                T0));
        assertThat(publisher.all()).hasSize(1);
    }

    @Test
    void wrongAndStaleClaimsAreAnsweredToTheClaimerOnly() {
        setUp(ScriptedRandom.of(DEAL_1).then(DEAL_2));
        long gameId = startAliceAndBob(3);
        publisher.clear();

        games.claim(gameId, "alice", Claim.SAME_SYMBOL, 1);   // wrong: A vs L
        games.claim(gameId, "bob", Claim.SAME_COLOR, 7);      // stale version

        assertThat(publisher.toUser("alice", ERRORS_QUEUE, ClaimRejected.class))
                .containsExactly(new ClaimRejected(gameId, ClaimStatus.WRONG, 1, T0));
        assertThat(publisher.toUser("bob", ERRORS_QUEUE, ClaimRejected.class))
                .containsExactly(new ClaimRejected(gameId, ClaimStatus.STALE, 1, T0));
        assertThat(publisher.all()).hasSize(2);
        assertThat(games.snapshot(gameId, "alice").scores())
                .containsExactly(new PlayerScoreDto("alice", 0), new PlayerScoreDto("bob", 0));
    }

    @Test
    void claimsFromOutsidersOrForUnknownGamesAreRejected() {
        setUp(new Random(1));
        long gameId = startAliceAndBob(3);

        assertThatThrownBy(() -> games.claim(gameId, "carol", Claim.BOTH, 1))
                .isInstanceOf(NotAGamePlayerException.class)
                .satisfies(e -> assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> games.claim(999L, "alice", Claim.BOTH, 1))
                .isInstanceOf(GameNotFoundException.class)
                .satisfies(e -> assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void claimsDoNotMoveTheRoundTimer() {
        setUp(ScriptedRandom.of(DEAL_1).then(DEAL_2));
        long gameId = startAliceAndBob(3);
        clock.advance(Duration.ofSeconds(5));

        games.claim(gameId, "alice", Claim.SAME_COLOR, 1);

        assertThat(scheduler.pendingTaskTimes()).containsExactly(T0.plusSeconds(10));
        assertThat(games.snapshot(gameId, "bob").roundEndsAt()).isEqualTo(T0.plusSeconds(10));
    }

    // DECISION Q17: a player can reload / reconnect and get the live view back.
    @Test
    void snapshotRestoresTheLiveViewForAPlayer() {
        setUp(ScriptedRandom.of(DEAL_1).then(DEAL_2));
        long gameId = startAliceAndBob(3);
        games.claim(gameId, "alice", Claim.SAME_COLOR, 1);

        GameSnapshotDto snapshot = games.snapshot(gameId, "bob");

        assertThat(snapshot).isEqualTo(new GameSnapshotDto(
                gameId, List.of("alice", "bob"), new GameSettingsDto(3, 10), GamePhase.IN_PROGRESS, 1,
                T0.plusSeconds(10),
                List.of(new PlayerScoreDto("alice", 1), new PlayerScoreDto("bob", 0)),
                new DealDto(2,
                        List.of(new PlayerCardDto("alice", "B", "blue"), new PlayerCardDto("bob", "C", "blue")),
                        new CardDto("B", "blue"))));
        assertThat(games.currentGameOf("BOB")).contains(snapshot);
        assertThat(games.currentGameOf("carol")).isEmpty();
        assertThatThrownBy(() -> games.snapshot(gameId, "carol")).isInstanceOf(NotAGamePlayerException.class);
        assertThatThrownBy(() -> games.snapshot(999L, "alice")).isInstanceOf(GameNotFoundException.class);
    }

    // DECISION (B8/B9): legacy rounds and the end were driven by every browser (N messages per change).
    // Now one server timer per game drives them; the result is recorded exactly once (D6).
    @Test
    void timerDrivesTheRoundsThenFinishesAndRecordsTheGameOnce() {
        // given: 2 rounds; alice scores once in round 1
        setUp(ScriptedRandom.of(DEAL_1).then(DEAL_2).then(DEAL_3));
        long gameId = startAliceAndBob(2);
        games.claim(gameId, "alice", Claim.SAME_COLOR, 1);
        publisher.clear();
        List<PlayerScoreDto> finalScores = List.of(new PlayerScoreDto("alice", 1), new PlayerScoreDto("bob", 0));

        // when: round 1 ends
        scheduler.advanceAndRun(Duration.ofSeconds(10));

        // then
        assertThat(publisher.onTopic(gameTopic(gameId), RoundStarted.class)).containsExactly(new RoundStarted(
                gameId, 2, 2, T0.plusSeconds(20), finalScores,
                new DealDto(3,
                        List.of(new PlayerCardDto("alice", "D", "red"), new PlayerCardDto("bob", "E", "green")),
                        new CardDto("D", "red")),
                T0.plusSeconds(10)));
        assertThat(scheduler.pendingTaskTimes()).containsExactly(T0.plusSeconds(20));
        assertThat(recorder.calls()).containsExactly("createGame:1");

        // when: round 2 (the last) ends
        publisher.clear();
        scheduler.advanceAndRun(Duration.ofSeconds(10));

        // then: event on the game topic only
        assertThat(publisher.onTopic(gameTopic(gameId), GameFinished.class)).containsExactly(new GameFinished(
                gameId, false, "alice", "bob", List.of("alice"), 1, List.of("bob"), 0, finalScores,
                T0.plusSeconds(20)));
        // and the game is recorded once, with a victory for the winner only (D5, D6)
        FakeGameRecorder.Row row = recorder.row(gameId);
        assertThat(row.status).isEqualTo(FakeGameRecorder.Status.FINISHED);
        assertThat(row.finishedAt).isEqualTo(T0.plusSeconds(20));
        assertThat(row.records).containsExactly(
                new PlayerGameRecord("alice", 1, true), new PlayerGameRecord("bob", 0, false));
        assertThat(row.finishCalls).isEqualTo(1);
        // and nothing is left running
        assertThat(scheduler.pendingTaskTimes()).isEmpty();
        scheduler.advanceAndRun(Duration.ofSeconds(60));
        assertThat(row.finishCalls).isEqualTo(1);
        // and the players are back in the lobby
        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerStatusChanged.class)).containsExactlyInAnyOrder(
                new PlayerStatusChanged("alice", PlayerStatus.ONLINE, T0.plusSeconds(20)),
                new PlayerStatusChanged("bob", PlayerStatus.ONLINE, T0.plusSeconds(20)));
        assertThat(presence.find("bob")).contains(new OnlinePlayerDto("bob", PlayerStatus.ONLINE));
        // and the live game is gone
        assertThat(games.currentGameOf("alice")).isEmpty();
        assertThat(games.isPlayer(gameId, "alice")).isFalse();
        assertThatThrownBy(() -> games.snapshot(gameId, "alice"))
                .isInstanceOf(GameFinishedException.class)
                .satisfies(e -> assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> games.claim(gameId, "alice", Claim.BOTH, 3)).isInstanceOf(GameNotFoundException.class);
    }

    @Test
    void resultIsReadFromTheRecordedGame() {
        setUp(ScriptedRandom.of(DEAL_1).then(DEAL_2));
        long gameId = startAliceAndBob(1);
        games.claim(gameId, "alice", Claim.SAME_COLOR, 1);

        // while the game is live
        assertThatThrownBy(() -> games.result(gameId, "alice"))
                .isInstanceOf(GameInProgressException.class)
                .satisfies(e -> assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.CONFLICT));

        scheduler.advanceAndRun(Duration.ofSeconds(10));

        assertThat(games.result(gameId, "BOB")).isEqualTo(new GameResultDto(
                gameId, T0.plusSeconds(10), false, "alice", "bob", List.of("alice"), 1, List.of("bob"), 0,
                List.of(new PlayerScoreDto("alice", 1), new PlayerScoreDto("bob", 0))));
        assertThatThrownBy(() -> games.result(gameId, "carol")).isInstanceOf(NotAGamePlayerException.class);
        assertThatThrownBy(() -> games.result(999L, "alice")).isInstanceOf(GameNotFoundException.class);
    }

    // DECISION Q3: legacy showed "null(Score: 4.9E-324 )" for an all-zero game and gave no victory.
    // Now it is a draw; the game still counts as played for both players (D6).
    @Test
    void gameWithoutAnyPointIsRecordedAsADraw() {
        setUp(new Random(5));
        long gameId = startAliceAndBob(1);
        publisher.clear();

        scheduler.advanceAndRun(Duration.ofSeconds(10));

        assertThat(publisher.onTopic(gameTopic(gameId), GameFinished.class)).singleElement().satisfies(finished -> {
            assertThat(finished.draw()).isTrue();
            assertThat(finished.winner()).isNull();
            assertThat(finished.loser()).isNull();
            assertThat(finished.leaders()).containsExactly("alice", "bob");
            assertThat(finished.trailers()).isEmpty();
        });
        assertThat(recorder.row(gameId).records).containsExactly(
                new PlayerGameRecord("alice", 0, false), new PlayerGameRecord("bob", 0, false));
        assertThat(games.result(gameId, "alice").draw()).isTrue();
    }

    // DECISION Q6: legacy saved stats only for each user's first game since restart (processedUsers) and
    // reused a single Partie row (PartieManager). Now every game gets its own row and its own records.
    @Test
    void everyGameIsRecordedSeparately() {
        setUp(new Random(9));

        long first = startAliceAndBob(1);
        scheduler.advanceAndRun(Duration.ofSeconds(10));
        long second = startAliceAndBob(1);
        scheduler.advanceAndRun(Duration.ofSeconds(10));

        assertThat(second).isNotEqualTo(first);
        assertThat(recorder.calls()).containsExactly(
                "createGame:" + first, "finishGame:" + first, "createGame:" + second, "finishGame:" + second);
        assertThat(recorder.rows()).allSatisfy(row -> {
            assertThat(row.status).isEqualTo(FakeGameRecorder.Status.FINISHED);
            assertThat(row.finishCalls).isEqualTo(1);
            assertThat(row.records).extracting(PlayerGameRecord::username).containsExactly("alice", "bob");
        });
    }

    // DECISION Q17: legacy kept no server state; a player who left lost the game view. Now the game goes on,
    // the score is kept and the player can come back.
    @Test
    void aDisconnectedPlayerKeepsTheirScoreAndCanRejoin() {
        // given
        setUp(ScriptedRandom.of(DEAL_1).then(DEAL_2).then(DEAL_3));
        long gameId = startAliceAndBob(2);
        games.claim(gameId, "alice", Claim.SAME_COLOR, 1);

        // when: alice drops, round 1 ends without her
        presence.disconnected("alice", "alice-tab1");
        scheduler.advanceAndRun(Duration.ofSeconds(10));
        publisher.clear();
        presence.connected("alice", "alice-tab2");

        // then: she is back "in game" with her score
        assertThat(publisher.onTopic(PRESENCE_TOPIC, PlayerOnline.class))
                .containsExactly(new PlayerOnline("alice", PlayerStatus.IN_GAME, T0.plusSeconds(10)));
        GameSnapshotDto snapshot = games.currentGameOf("alice").orElseThrow();
        assertThat(snapshot.round()).isEqualTo(2);
        assertThat(snapshot.scores()).containsExactly(new PlayerScoreDto("alice", 1), new PlayerScoreDto("bob", 0));

        // and her score is recorded at the end
        scheduler.advanceAndRun(Duration.ofSeconds(10));
        assertThat(recorder.row(gameId).records).containsExactly(
                new PlayerGameRecord("alice", 1, true), new PlayerGameRecord("bob", 0, false));
    }

    @Test
    void aPlayerWhoNeverComesBackIsStillRecorded() {
        setUp(new Random(2));
        long gameId = startAliceAndBob(1);

        presence.disconnected("bob", "bob-tab1");
        scheduler.advanceAndRun(Duration.ofSeconds(10));

        assertThat(recorder.row(gameId).records).extracting(PlayerGameRecord::username).containsExactly("alice", "bob");
    }

    // DECISION Q16, with the real services wired together: invitation → game start → "in game" → 409.
    @Test
    void playersInAGameCannotBeInvitedUntilItEnds() {
        // given
        setUp(new Random(4));
        InvitationService invitations = new InvitationService(clock, scheduler, publisher, presence, games);
        InvitationDto invitation = invitations.create("alice",
                new CreateInvitationRequest(List.of("bob"), new GameSettingsDto(1, 10)));

        // when
        InvitationDto accepted = invitations.accept(invitation.id(), "bob");

        // then
        assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);
        long gameId = accepted.gameId();
        assertThat(games.isPlayer(gameId, "bob")).isTrue();
        assertThat(recorder.row(gameId).settings).isEqualTo(new GameSettings(1, Duration.ofSeconds(10)));
        assertThatThrownBy(() -> invitations.create("carol",
                new CreateInvitationRequest(List.of("bob"), null)))
                .isInstanceOf(PlayerUnavailableException.class);

        // when the game ends
        scheduler.advanceAndRun(Duration.ofSeconds(10));

        // then bob can be invited again
        assertThat(invitations.create("carol", new CreateInvitationRequest(List.of("bob"), null)).status())
                .isEqualTo(InvitationStatus.PENDING);
    }
}
