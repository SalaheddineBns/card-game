package com.cardgame.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;

class HttpSessionEndListenerTest {

    @Test
    void closesTheStompConnectionsOfAnExpiredHttpSession() {
        RealtimeSessionRegistry registry = mock(RealtimeSessionRegistry.class);
        MockHttpSession session = new MockHttpSession(null, "expired-session");

        new HttpSessionEndListener(registry).onSessionDestroyed(new HttpSessionDestroyedEvent(session));

        verify(registry).closeAll("expired-session");
    }
}
