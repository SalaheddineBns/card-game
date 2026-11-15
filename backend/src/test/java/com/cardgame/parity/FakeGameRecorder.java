package com.cardgame.parity;

import com.cardgame.game.FinishedGame;
import com.cardgame.game.GameRecorder;
import com.cardgame.game.GameStatus;
import com.cardgame.game.PlayerGameRecord;
import com.cardgame.game.engine.GameSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory persistence port: plain storage, no rules. Ids start at 1. */
final class FakeGameRecorder implements GameRecorder {

    enum Status { IN_PROGRESS, FINISHED, ABANDONED }

    static final class Row {
        final long id;
        final GameSettings settings;
        final Instant startedAt;
        Status status = Status.IN_PROGRESS;
        Instant finishedAt;
        List<PlayerGameRecord> records = List.of();
        int finishCalls;

        Row(long id, GameSettings settings, Instant startedAt) {
            this.id = id;
            this.settings = settings;
            this.startedAt = startedAt;
        }
    }

    private final Map<Long, Row> rows = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private long nextId = 1;

    @Override
    public long createGame(GameSettings settings, Instant startedAt) {
        Row row = new Row(nextId++, settings, startedAt);
        rows.put(row.id, row);
        calls.add("createGame:" + row.id);
        return row.id;
    }

    @Override
    public void finishGame(long gameId, Instant finishedAt, List<PlayerGameRecord> records) {
        Row row = rows.get(gameId);
        if (row == null) {
            throw new IllegalStateException("unknown game " + gameId);
        }
        row.status = Status.FINISHED;
        row.finishedAt = finishedAt;
        row.records = List.copyOf(records);
        row.finishCalls++;
        calls.add("finishGame:" + gameId);
    }

    @Override
    public Optional<FinishedGame> findFinished(long gameId) {
        Row row = rows.get(gameId);
        if (row == null || row.status != Status.FINISHED) {
            return Optional.empty();
        }
        return Optional.of(new FinishedGame(row.id, row.finishedAt, row.records));
    }

    @Override
    public int abandonInProgressStartedBefore(Instant cutoff) {
        int count = 0;
        for (Row row : rows.values()) {
            if (row.status == Status.IN_PROGRESS && row.startedAt.isBefore(cutoff)) {
                row.status = Status.ABANDONED;
                row.finishedAt = cutoff;
                count++;
            }
        }
        calls.add("abandonInProgressStartedBefore:" + count);
        return count;
    }

    @Override
    public void abandonGame(long gameId, Instant at) {
        Row row = rows.get(gameId);
        if (row != null && row.status == Status.IN_PROGRESS) {
            row.status = Status.ABANDONED;
            row.finishedAt = at;
        }
        calls.add("abandonGame:" + gameId);
    }

    @Override
    public Optional<GameStatus> findStatus(long gameId) {
        return Optional.ofNullable(rows.get(gameId)).map(row -> GameStatus.valueOf(row.status.name()));
    }

    Row row(long gameId) {
        return rows.get(gameId);
    }

    List<Row> rows() {
        return List.copyOf(rows.values());
    }

    List<String> calls() {
        return List.copyOf(calls);
    }
}
