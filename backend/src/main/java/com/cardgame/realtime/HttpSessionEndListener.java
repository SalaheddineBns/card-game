package com.cardgame.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * G11: when an HTTP session ends (timeout or invalidation), its STOMP connections are closed, so a socket never
 * outlives the login it was opened with.
 */
@Component
public class HttpSessionEndListener {

    private final RealtimeSessionRegistry sessions;

    public HttpSessionEndListener(RealtimeSessionRegistry sessions) {
        this.sessions = sessions;
    }

    @EventListener
    public void onSessionDestroyed(SessionDestroyedEvent event) {
        sessions.closeAll(event.getId());
    }
}
