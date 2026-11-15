package com.cardgame.game;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory live games (D22). Thread-safe. */
@Component
public class GameSessionRegistry {

    private final Map<Long, GameSession> sessions = new ConcurrentHashMap<>();

    public GameSessionRegistry() {
    }

    public boolean isLive(long gameId) {
        return sessions.containsKey(gameId);
    }

    /** Id of the live game this player is in, ignoring case. */
    public Optional<Long> activeGameOf(String username) {
        return sessions.values().stream()
                .filter(s -> s.player(username).isPresent())
                .map(GameSession::gameId)
                .findFirst();
    }

    void register(GameSession session) {
        if (sessions.putIfAbsent(session.gameId(), session) != null) {
            throw new IllegalStateException("Game " + session.gameId() + " is already live");
        }
    }

    Optional<GameSession> find(long gameId) {
        return Optional.ofNullable(sessions.get(gameId));
    }

    void remove(long gameId) {
        sessions.remove(gameId);
    }
}
