package com.cardgame.game.dto;

import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;
import java.util.List;

public record GameFinished(long gameId, boolean draw, String winner, String loser, List<String> leaders, int topScore, List<String> trailers, int bottomScore, List<PlayerScoreDto> scores, Instant sentAt) implements RealtimeEvent {
}
