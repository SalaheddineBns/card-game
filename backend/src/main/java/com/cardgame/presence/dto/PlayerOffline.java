package com.cardgame.presence.dto;

import com.cardgame.realtime.RealtimeEvent;
import java.time.Instant;

public record PlayerOffline(String username, Instant sentAt) implements RealtimeEvent {
}
