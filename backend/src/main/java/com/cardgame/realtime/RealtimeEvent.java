package com.cardgame.realtime;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;

/**
 * Every server → client STOMP payload. Serialised with a {@code "type"} property holding the simple class
 * name (or the {@code @JsonTypeName}) and a {@code "sentAt"} instant (ISO-8601).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface RealtimeEvent {

    Instant sentAt();
}
