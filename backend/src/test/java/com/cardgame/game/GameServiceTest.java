package com.cardgame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cardgame.common.DomainException;
import com.cardgame.game.dto.GameFinished;
import com.cardgame.game.engine.GameEngine;
import com.cardgame.game.engine.GameSettings;
import com.cardgame.presence.PresenceService;
import com.cardgame.realtime.RealtimePublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;

/** D25 (410 for abandoned games) and G5 (retry, then abandon) with a mocked persistence port. */
class GameServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private GameRecorder recorder;
    private TaskScheduler scheduler;
    private RealtimePublisher publisher;
    private PresenceService presence;
    private GameService games;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        recorder = mock(GameRecorder.class);
        scheduler = mock(TaskScheduler.class);
        publisher = mock(RealtimePublisher.class);
        presence = new PresenceService(clock, publisher);
        doReturn(mock(ScheduledFuture.class)).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        games = new GameService(new GameEngine(new Random(1), clock), new GameSessionRegistry(), recorder,
                presence, publisher, scheduler, clock);
    }

    @Test
    void snapshotAndResultOfAnAbandonedGameAre410ForAnyUser() {
        given(recorder.findStatus(5L)).willReturn(Optional.of(GameStatus.ABANDONED));

        assertThatThrownBy(() -> games.snapshot(5L, "anyone"))
                .isInstanceOf(GameAbandonedException.class)
                .satisfies(e -> {
                    assertThat(((DomainException) e).status()).isEqualTo(HttpStatus.GONE);
                    assertThat(((DomainException) e).title()).isEqualTo("Partie interrompue");
                });
        assertThatThrownBy(() -> games.result(5L, "anyone")).isInstanceOf(GameAbandonedException.class);
        assertThat(games.isPlayer(5L, "anyone")).isFalse();
    }

    @Test
    void aGameStillInProgressInTheDatabaseButNotLiveIsNotFound() {
        given(recorder.findStatus(6L)).willReturn(Optional.of(GameStatus.IN_PROGRESS));

        assertThatThrownBy(() -> games.snapshot(6L, "alice")).isInstanceOf(GameNotFoundException.class);
        assertThatThrownBy(() -> games.result(6L, "alice")).isInstanceOf(GameNotFoundException.class);
    }

    @Test
    void finishedGameStillAnswers409OnTheSnapshot() {
        given(recorder.findStatus(7L)).willReturn(Optional.of(GameStatus.FINISHED));

        assertThatThrownBy(() -> games.snapshot(7L, "alice")).isInstanceOf(GameFinishedException.class);
    }

    @Test
    void savingTheEndIsRetriedOnce() {
        long gameId = startOneRoundGame();
        willThrow(new IllegalStateException("db hiccup")).willDoNothing()
                .given(recorder).finishGame(anyLong(), any(), any());

        runScheduledTick();

        verify(recorder, times(2)).finishGame(anyLong(), any(), any());
        verify(recorder, never()).abandonGame(anyLong(), any());
        verify(publisher).toTopic(any(), any(GameFinished.class));
        assertThat(games.isPlayer(gameId, "alice")).isFalse();
    }

    @Test
    void whenSavingTheEndFailsTwiceTheGameIsAbandonedAndThePlayersAreFreed() {
        long gameId = startOneRoundGame();
        willThrow(new IllegalStateException("db down")).given(recorder).finishGame(anyLong(), any(), any());

        runScheduledTick();

        verify(recorder, times(2)).finishGame(anyLong(), any(), any());
        verify(recorder).abandonGame(gameId, T0);
        assertThat(presence.isInGame("alice")).isFalse();
        assertThat(games.currentGameOf("bob")).isEmpty();
        // and the result then answers 410
        given(recorder.findStatus(gameId)).willReturn(Optional.of(GameStatus.ABANDONED));
        assertThatThrownBy(() -> games.result(gameId, "alice")).isInstanceOf(GameAbandonedException.class);
    }

    private long startOneRoundGame() {
        given(recorder.createGame(any(), any())).willReturn(42L);
        return games.startGame(List.of("alice", "bob"), new GameSettings(1, Duration.ofSeconds(10)));
    }

    private void runScheduledTick() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(task.capture(), any(Instant.class));
        task.getValue().run();
    }

}
