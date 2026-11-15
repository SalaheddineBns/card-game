package com.cardgame.game;

import com.cardgame.game.dto.ClaimRejected;
import com.cardgame.game.dto.GameFinished;
import com.cardgame.game.dto.GameResultDto;
import com.cardgame.game.dto.GameSnapshotDto;
import com.cardgame.game.dto.GameStarted;
import com.cardgame.game.dto.PlayerScoreDto;
import com.cardgame.game.dto.RoundStarted;
import com.cardgame.game.dto.ScoresUpdated;
import com.cardgame.game.engine.Claim;
import com.cardgame.game.engine.ClaimOutcome;
import com.cardgame.game.engine.GameEngine;
import com.cardgame.game.engine.GamePhase;
import com.cardgame.game.engine.GameResult;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.game.engine.GameState;
import com.cardgame.game.engine.PlayerOutcome;
import com.cardgame.presence.PresenceService;
import com.cardgame.realtime.Destinations;
import com.cardgame.realtime.RealtimePublisher;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SequencedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * Orchestrates live games: start, claims, the server round timer, the end of the game (rows 10-14).
 * Rules come from {@link GameEngine}; every operation on one game holds that game's {@link GameSession} lock.
 */
@Service
public class GameService implements GameLauncher {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameEngine engine;
    private final GameSessionRegistry sessions;
    private final GameRecorder recorder;
    private final PresenceService presence;
    private final RealtimePublisher publisher;
    private final TaskScheduler scheduler;
    private final Clock clock;

    public GameService(GameEngine engine, GameSessionRegistry sessions, GameRecorder recorder,
                       PresenceService presence, RealtimePublisher publisher,
                       @Qualifier("gameTaskScheduler") TaskScheduler scheduler, Clock clock) {
        this.engine = engine;
        this.sessions = sessions;
        this.recorder = recorder;
        this.presence = presence;
        this.publisher = publisher;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Override
    public long startGame(List<String> players, GameSettings settings) {
        if (players.size() < 2 || new HashSet<>(players.stream().map(p -> p.toLowerCase(Locale.ROOT)).toList())
                .size() != players.size()) {
            throw new IllegalArgumentException("A game needs at least 2 distinct players: " + players);
        }
        long gameId = recorder.createGame(settings, clock.instant());
        GameSession session = new GameSession(gameId, engine.start(players, settings));
        synchronized (session) {
            sessions.register(session);
            presence.setInGame(players, true);
            scheduleTick(session);
        }
        players.forEach(p -> publisher.toUser(p, Destinations.GAMES_QUEUE, new GameStarted(gameId, clock.instant())));
        return gameId;
    }

    public void claim(long gameId, String username, Claim claim, int dealVersion) {
        GameSession session = live(gameId);
        String player = session.player(username).orElseThrow(() -> new NotAGamePlayerException(gameId));
        synchronized (session) {
            if (session.over()) {
                throw new GameNotFoundException(gameId);
            }
            ClaimOutcome outcome = engine.claim(session.state(), player, claim, dealVersion);
            if (outcome.correct()) {
                session.state(outcome.state());
                GameState state = outcome.state();
                publisher.toTopic(Destinations.gameTopic(gameId), new ScoresUpdated(gameId, player,
                        GameDtos.scores(state.scores()), GameDtos.deal(state), clock.instant()));
            } else {
                publisher.toUser(player, Destinations.ERRORS_QUEUE, new ClaimRejected(gameId, outcome.status(),
                        session.state().dealVersion(), clock.instant()));
            }
        }
    }

    public GameSnapshotDto snapshot(long gameId, String username) {
        Optional<GameSession> live = sessions.find(gameId);
        if (live.isEmpty()) {
            GameStatus status = recorder.findStatus(gameId).orElse(null);
            if (status == GameStatus.FINISHED) {
                throw new GameFinishedException(gameId);
            }
            if (status == GameStatus.ABANDONED) {
                throw new GameAbandonedException();
            }
            throw new GameNotFoundException(gameId);
        }
        GameSession session = live.get();
        if (session.player(username).isEmpty()) {
            throw new NotAGamePlayerException(gameId);
        }
        synchronized (session) {
            return GameDtos.snapshot(gameId, session.state());
        }
    }

    public Optional<GameSnapshotDto> currentGameOf(String username) {
        return sessions.activeGameOf(username).flatMap(id -> sessions.find(id)).map(session -> {
            synchronized (session) {
                return GameDtos.snapshot(session.gameId(), session.state());
            }
        });
    }

    public GameResultDto result(long gameId, String username) {
        if (sessions.isLive(gameId)) {
            throw new GameInProgressException(gameId);
        }
        GameStatus status = recorder.findStatus(gameId).orElse(null);
        if (status == GameStatus.ABANDONED) {
            throw new GameAbandonedException();
        }
        FinishedGame finished = (status == GameStatus.FINISHED ? recorder.findFinished(gameId) : Optional.<FinishedGame>empty())
                .orElseThrow(() -> new GameNotFoundException(gameId));
        String wanted = username.toLowerCase(Locale.ROOT);
        if (finished.records().stream().noneMatch(r -> r.username().toLowerCase(Locale.ROOT).equals(wanted))) {
            throw new NotAGamePlayerException(gameId);
        }
        SequencedMap<String, Integer> scores = new LinkedHashMap<>();
        finished.records().forEach(r -> scores.put(r.username(), r.score()));
        GameResult result = GameEngine.determineResult(scores);
        return new GameResultDto(gameId, finished.finishedAt(), result.isDraw(), result.winner(), result.loser(),
                result.leaders(), result.topScore(), result.trailers(), result.bottomScore(), GameDtos.scores(scores));
    }

    /** Used by the STOMP SUBSCRIBE guard for /topic/game/{id}. */
    public boolean isPlayer(long gameId, String username) {
        return sessions.find(gameId).flatMap(s -> s.player(username)).isPresent();
    }

    private GameSession live(long gameId) {
        return sessions.find(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
    }

    /** Caller holds the session lock. */
    private void scheduleTick(GameSession session) {
        session.timer(scheduler.schedule(() -> onRoundTimer(session), session.state().roundEndsAt()));
    }

    private void onRoundTimer(GameSession session) {
        synchronized (session) {
            if (session.over() || session.state().phase() != GamePhase.IN_PROGRESS) {
                return;
            }
            long gameId = session.gameId();
            GameState state = engine.roundTimerExpired(session.state());
            session.state(state);
            if (state.phase() == GamePhase.IN_PROGRESS) {
                publisher.toTopic(Destinations.gameTopic(gameId), new RoundStarted(gameId, state.currentRound(),
                        state.settings().totalRounds(), state.roundEndsAt(), GameDtos.scores(state.scores()),
                        GameDtos.deal(state), clock.instant()));
                scheduleTick(session);
            } else {
                finish(session, state);
            }
        }
    }

    /** Caller holds the session lock. */
    private void finish(GameSession session, GameState state) {
        long gameId = session.gameId();
        session.markOver();
        session.timer(null);
        GameResult result = engine.result(state);
        List<PlayerGameRecord> records = result.outcomes().stream()
                .map((PlayerOutcome o) -> new PlayerGameRecord(o.player(), o.score(), o.victory()))
                .toList();
        recordEnd(gameId, records);
        List<PlayerScoreDto> scores = GameDtos.scores(state.scores());
        publisher.toTopic(Destinations.gameTopic(gameId), new GameFinished(gameId, result.isDraw(), result.winner(),
                result.loser(), result.leaders(), result.topScore(), result.trailers(), result.bottomScore(), scores,
                clock.instant()));
        presence.setInGame(state.players(), false);
        sessions.remove(gameId);
    }

    /**
     * G5: saves the end of the game, retrying once (finishGame is one transaction, so a failed attempt left
     * nothing behind). If both attempts fail the game is marked ABANDONED so the result answers 410 at once.
     * Never throws: the players must not stay stuck "in game".
     */
    private void recordEnd(long gameId, List<PlayerGameRecord> records) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                recorder.finishGame(gameId, clock.instant(), records);
                return;
            } catch (RuntimeException e) {
                log.error("Could not record the end of game {} (attempt {}/2)", gameId, attempt, e);
            }
        }
        try {
            recorder.abandonGame(gameId, clock.instant());
        } catch (RuntimeException e) {
            log.error("Could not mark game {} as abandoned; it will be abandoned at the next startup", gameId, e);
        }
    }
}
