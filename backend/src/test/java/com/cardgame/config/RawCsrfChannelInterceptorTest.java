package com.cardgame.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class RawCsrfChannelInterceptorTest {

    private static final CsrfToken TOKEN = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "raw-cookie-value");

    private final RawCsrfChannelInterceptor interceptor = new RawCsrfChannelInterceptor();

    @Test
    void acceptsTheRawCookieValueOnConnect() {
        Message<?> connect = connect("raw-cookie-value", TOKEN);

        assertThat(interceptor.preSend(connect, null)).isSameAs(connect);
    }

    @Test
    void rejectsAWrongOrMissingHeader() {
        assertThatThrownBy(() -> interceptor.preSend(connect("other", TOKEN), null))
                .isInstanceOf(InvalidCsrfTokenException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect(null, TOKEN), null))
                .isInstanceOf(InvalidCsrfTokenException.class);
    }

    @Test
    void rejectsAConnectWithoutAHandshakeToken() {
        assertThatThrownBy(() -> interceptor.preSend(connect("raw-cookie-value", null), null))
                .isInstanceOf(MissingCsrfTokenException.class);
    }

    @Test
    void ignoresOtherFrames() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionAttributes(new HashMap<>());
        Message<byte[]> subscribe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(subscribe, null)).isSameAs(subscribe);
    }

    private static Message<byte[]> connect(String header, CsrfToken sessionToken) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Map<String, Object> attributes = new HashMap<>();
        if (sessionToken != null) {
            attributes.put(CsrfToken.class.getName(), sessionToken);
        }
        accessor.setSessionAttributes(attributes);
        if (header != null) {
            accessor.setNativeHeader("X-XSRF-TOKEN", header);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
