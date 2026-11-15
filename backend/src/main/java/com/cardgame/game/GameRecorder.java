package com.cardgame.game;

import com.cardgame.game.engine.GameSettings;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for games; {@link JpaGameRecorder} owns the transactions. */
public interface GameRecorder {

    /** Inserts a games row with status IN_PROGRESS; returns its id (D22). */
    long createGame(GameSettings settings, Instant startedAt);

    /** One transaction: FINISHED + finished_at, one participation per record, player stats (D6). */
    void finishGame(long gameId, Instant finishedAt, List<PlayerGameRecord> records);

    /** Only FINISHED games; records in player order. */
    Optional<FinishedGame> findFinished(long gameId);
}
