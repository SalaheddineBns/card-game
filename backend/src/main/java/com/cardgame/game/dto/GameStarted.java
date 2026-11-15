package com.cardgame.game.dto;

import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;

public record GameStarted(long gameId, Instant sentAt) implements RealtimeEvent {
}
