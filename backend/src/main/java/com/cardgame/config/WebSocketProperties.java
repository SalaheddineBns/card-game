package com.cardgame.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cardgame.websocket.*}.
 *
 * @param allowedOriginPatterns extra origins allowed to open {@code /ws}. Empty (default) = same origin only,
 *     which is right when the app is served by Spring (Docker image) or through the Angular dev proxy. Escape hatch,
 *     e.g. {@code CARDGAME_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS=https://*.up.railway.app}, if a proxy hides the host.
 */
@ConfigurationProperties("cardgame.websocket")
public record WebSocketProperties(@DefaultValue List<String> allowedOriginPatterns) {
}
