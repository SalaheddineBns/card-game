package com.cardgame.game.dto;

import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;
import java.util.List;

public record ScoresUpdated(long gameId, String scoredBy, List<PlayerScoreDto> scores, DealDto deal, Instant sentAt) implements RealtimeEvent {
}
