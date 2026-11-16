package com.cardgame.auth;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionCreatedEvent;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * Live HTTP sessions, fed by the {@code HttpSessionEventPublisher} registered in {@code SecurityConfig}. Used by
 * the demo reset (D29) to log everybody out. The session object survives Tomcat's id change at login, so it
 * is tracked by identity, not by id.
 */
@Component
public class HttpSessionTracker {

    private final Set<HttpSession> sessions = ConcurrentHashMap.newKeySet();

    @EventListener
    public void onCreated(HttpSessionCreatedEvent event) {
        sessions.add(event.getSession());
    }

    @EventListener
    public void onDestroyed(HttpSessionDestroyedEvent event) {
        sessions.remove(event.getSession());
    }

    /** Invalidates every live session (logs everybody out). @return the number of sessions invalidated */
    public int invalidateAll() {
        int count = 0;
        for (HttpSession session : List.copyOf(sessions)) {
            sessions.remove(session);
            try {
                session.invalidate();
                count++;
            } catch (IllegalStateException alreadyInvalid) {
                // expired meanwhile
            }
        }
        return count;
    }

    int size() {
        return sessions.size();
    }
}
