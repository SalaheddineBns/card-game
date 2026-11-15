package com.cardgame.realtime;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;

/** {@code {"type":"Error", status, title, detail, sentAt}} on {@code /user/queue/errors} (French texts). */
@JsonTypeName("Error")
public record ErrorEvent(int status, String title, String detail, Instant sentAt) implements RealtimeEvent {
}
