package com.cardgame.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * Open WebSocket sessions grouped by the HTTP session they were opened from, so logout can close them
 * (plan row 3). Closing a socket triggers the usual disconnect → presence update.
 */
@Component
public class RealtimeSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessionRegistry.class);

    private final Map<String, Set<WebSocketSession>> byHttpSession = new ConcurrentHashMap<>();

    public void opened(WebSocketSession session) {
        String httpSessionId = httpSessionIdOf(session);
        if (httpSessionId != null) {
            byHttpSession.computeIfAbsent(httpSessionId, id -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    public void closed(WebSocketSession session) {
        String httpSessionId = httpSessionIdOf(session);
        if (httpSessionId != null) {
            byHttpSession.computeIfPresent(httpSessionId, (id, sessions) -> {
                sessions.remove(session);
                return sessions.isEmpty() ? null : sessions;
            });
        }
    }

    /** Closes every WebSocket opened from this HTTP session. */
    public void closeAll(String httpSessionId) {
        Set<WebSocketSession> sessions = byHttpSession.remove(httpSessionId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                session.close(CloseStatus.NORMAL.withReason("logout"));
            } catch (IOException e) {
                log.debug("Could not close WebSocket session {}", session.getId(), e);
            }
        }
    }

    private static String httpSessionIdOf(WebSocketSession session) {
        Object id = session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
        return id instanceof String s ? s : null;
    }
}
