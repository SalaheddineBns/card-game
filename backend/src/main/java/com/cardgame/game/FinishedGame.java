package com.cardgame.game;

import java.time.Instant;
import java.util.List;

public record FinishedGame(long gameId, Instant finishedAt, List<PlayerGameRecord> records) {

    public FinishedGame {
        records = List.copyOf(records);
    }
}
