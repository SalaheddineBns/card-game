package com.cardgame.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/**
 * CSRF check of the STOMP CONNECT frame. The SPA sends the <em>raw</em> {@code XSRF-TOKEN} cookie value in the
 * {@code X-XSRF-TOKEN} native header (Spring Security's default interceptor only accepts the XOR-masked token).
 * The expected token is the one {@code CsrfTokenHandshakeInterceptor} copied from the handshake request.
 */
public final class RawCsrfChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.CONNECT) {
            return message;
        }
        Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(message.getHeaders());
        CsrfToken expected = attributes == null ? null : (CsrfToken) attributes.get(CsrfToken.class.getName());
        if (expected == null) {
            throw new MissingCsrfTokenException(null);
        }
        String actual = SimpMessageHeaderAccessor.wrap(message).getFirstNativeHeader(expected.getHeaderName());
        if (actual == null || !MessageDigest.isEqual(
                expected.getToken().getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidCsrfTokenException(expected, actual);
        }
        return message;
    }
}
