package com.cardgame.config;

import com.cardgame.realtime.RealtimeSessionRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * STOMP over native WebSocket at {@code /ws} (same origin). App prefix {@code /app}, broker {@code /topic} and
 * {@code /queue}, user prefix {@code /user}, heartbeats 10 s / 10 s. See docs/realtime-protocol.md.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final long HEARTBEAT_MILLIS = 10_000;

    private final TaskScheduler heartbeatScheduler;
    private final RealtimeSessionRegistry sessionRegistry;
    private final WebSocketProperties properties;

    public WebSocketConfig(@Qualifier("stompHeartbeatScheduler") TaskScheduler heartbeatScheduler,
                           RealtimeSessionRegistry sessionRegistry, WebSocketProperties properties) {
        this.heartbeatScheduler = heartbeatScheduler;
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint("/ws")
                // keeps the HTTP session id on the socket so logout can close it
                .addInterceptors(new HttpSessionHandshakeInterceptor());
        if (!properties.allowedOriginPatterns().isEmpty()) {
            endpoint.setAllowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new));
        }
        // No setPreserveReceiveOrder(true): with it, frames rejected by an interceptor (bad CSRF on CONNECT,
        // forbidden SUBSCRIBE) are only logged and the client never gets an ERROR frame. Each game is
        // serialised in GameService, so claim correctness does not depend on inbound order.
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessionRegistry.opened(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessionRegistry.closed(session);
                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }

}
