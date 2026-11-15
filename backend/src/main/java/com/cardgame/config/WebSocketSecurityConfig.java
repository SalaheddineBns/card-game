package com.cardgame.config;

import com.cardgame.game.GameService;
import com.cardgame.realtime.Destinations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.MessageAuthorizationContext;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

/**
 * STOMP authorization. The principal is the logged-in player of the handshake's HTTP session (an anonymous
 * handshake is refused with 401 by the HTTP filter chain).
 */
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages, ObjectProvider<GameService> games) {
        // GameService is looked up lazily: it depends (via the publisher) on the broker configuration that uses this bean
        return messages
                .simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()
                .nullDestMatcher().authenticated()                       // CONNECT, heartbeats, UNSUBSCRIBE
                .simpSubscribeDestMatchers("/user/queue/**", Destinations.PRESENCE_TOPIC).authenticated()
                // D14: only the game's players may follow its topic
                .simpSubscribeDestMatchers(Destinations.GAME_TOPIC_PREFIX + "{gameId}").access(
                        (authentication, context) -> new AuthorizationDecision(isPlayer(games.getObject(), authentication.get(), context)))
                .simpDestMatchers("/app/**").authenticated()             // client SEND
                .anyMessage().denyAll()                                  // e.g. SEND to /topic
                .build();
    }

    /** Replaces Spring Security's XOR-only CONNECT check (bean name is the documented override point). */
    @Bean(name = "csrfChannelInterceptor")
    ChannelInterceptor csrfChannelInterceptor() {
        return new RawCsrfChannelInterceptor();
    }

    private static boolean isPlayer(GameService games, Authentication authentication,
                                    MessageAuthorizationContext<?> context) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        try {
            long gameId = Long.parseLong(context.getVariables().get("gameId"));
            return games.isPlayer(gameId, authentication.getName());
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
