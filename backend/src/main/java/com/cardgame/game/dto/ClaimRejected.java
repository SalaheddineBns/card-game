package com.cardgame.game.dto;

import com.cardgame.game.engine.ClaimStatus;
import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;

public record ClaimRejected(long gameId, ClaimStatus reason, int currentDealVersion, Instant sentAt) implements RealtimeEvent {
}
