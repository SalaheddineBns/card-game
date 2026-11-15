package com.cardgame.game.dto;

import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;
import java.util.List;

public record RoundStarted(long gameId, int round, int totalRounds, Instant roundEndsAt, List<PlayerScoreDto> scores, DealDto deal, Instant sentAt) implements RealtimeEvent {
}
