package com.cardgame.presence;

import com.cardgame.presence.dto.OnlinePlayerDto;
import com.cardgame.presence.dto.PlayerOffline;
import com.cardgame.presence.dto.PlayerOnline;
import com.cardgame.presence.dto.PlayerStatusChanged;
import com.cardgame.realtime.Destinations;
import com.cardgame.realtime.RealtimePublisher;
import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Who is online (≥ 1 STOMP session, D15) and who is in a game (D16). In memory; all methods are
 * serialised on this instance.
 */
@Service
public class PresenceService {

    private record Online(String username, Set<String> sessionIds) {
    }

    private final Clock clock;
    private final RealtimePublisher publisher;
    /** lower-case username → online player. */
    private final Map<String, Online> online = new HashMap<>();
    /** lower-case usernames currently in a game; kept while offline (D17). */
    private final Set<String> inGame = new HashSet<>();

    public PresenceService(Clock clock, RealtimePublisher publisher) {
        this.clock = clock;
        this.publisher = publisher;
    }

    /** First session of this user → {@code PlayerOnline}. */
    public synchronized void connected(String username, String sessionId) {
        String key = key(username);
        Online entry = online.get(key);
        if (entry == null) {
            entry = new Online(username, new LinkedHashSet<>());
            online.put(key, entry);
        }
        boolean first = entry.sessionIds().isEmpty();
        entry.sessionIds().add(sessionId);
        if (first) {
            publisher.toTopic(Destinations.PRESENCE_TOPIC,
                    new PlayerOnline(entry.username(), statusOf(key), clock.instant()));
        }
    }

    /** Last session of this user → {@code PlayerOffline}. Unknown ids are ignored. */
    public synchronized void disconnected(String username, String sessionId) {
        String key = key(username);
        Online entry = online.get(key);
        if (entry == null || !entry.sessionIds().remove(sessionId)) {
            return;
        }
        if (entry.sessionIds().isEmpty()) {
            online.remove(key);
            publisher.toTopic(Destinations.PRESENCE_TOPIC, new PlayerOffline(entry.username(), clock.instant()));
        }
    }

    public synchronized Optional<OnlinePlayerDto> find(String username) {
        String key = key(username);
        return Optional.ofNullable(online.get(key)).map(e -> new OnlinePlayerDto(e.username(), statusOf(key)));
    }

    public synchronized List<OnlinePlayerDto> onlinePlayers(String excludedUsername) {
        String excluded = excludedUsername == null ? null : key(excludedUsername);
        return online.entrySet().stream()
                .filter(e -> !e.getKey().equals(excluded))
                .map(e -> new OnlinePlayerDto(e.getValue().username(), statusOf(e.getKey())))
                .sorted(Comparator.comparing(OnlinePlayerDto::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** True while the player is in a running game, online or not. */
    public synchronized boolean isInGame(String username) {
        return inGame.contains(key(username));
    }

    /** Marks players as in a game or not; online players whose status changes → {@code PlayerStatusChanged}. */
    public synchronized void setInGame(Collection<String> usernames, boolean playing) {
        for (String username : usernames) {
            String key = key(username);
            boolean changed = playing ? inGame.add(key) : inGame.remove(key);
            Online entry = online.get(key);
            if (changed && entry != null) {
                publisher.toTopic(Destinations.PRESENCE_TOPIC,
                        new PlayerStatusChanged(entry.username(), statusOf(key), clock.instant()));
            }
        }
    }

    /**
     * Forgets everyone (demo reset, D29). Publishes nothing: the STOMP connections are closed first, and
     * their late disconnect events are ignored because their sessions are unknown by then.
     *
     * @return the number of players that were online
     */
    public synchronized int clear() {
        int count = online.size();
        online.clear();
        inGame.clear();
        return count;
    }

    private PlayerStatus statusOf(String key) {
        return inGame.contains(key) ? PlayerStatus.IN_GAME : PlayerStatus.ONLINE;
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
