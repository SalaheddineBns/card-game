package com.cardgame.presence;

import java.security.Principal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** STOMP session lifecycle → presence (row 7). */
@Component
public class PresenceEventListener {

    private final PresenceService presence;

    public PresenceEventListener(PresenceService presence) {
        this.presence = presence;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (user != null && sessionId != null) {
            presence.connected(user.getName(), sessionId);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) {
            presence.disconnected(user.getName(), event.getSessionId());
        }
    }
}
