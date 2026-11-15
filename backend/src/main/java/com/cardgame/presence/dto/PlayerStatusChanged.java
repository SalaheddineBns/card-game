package com.cardgame.presence.dto;

import com.cardgame.presence.PlayerStatus;
import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;

public record PlayerStatusChanged(String username, PlayerStatus status, Instant sentAt) implements RealtimeEvent {
}
