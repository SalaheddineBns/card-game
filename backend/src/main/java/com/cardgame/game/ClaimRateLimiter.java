package com.cardgame.game;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Per-player fixed one-second window for STOMP claims (G10). Thread-safe. */
@Component
public class ClaimRateLimiter {

    private record Window(long second, int count) {
    }

    private final Clock clock;
    private final int maxPerSecond;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public ClaimRateLimiter(Clock clock, ClaimProperties properties) {
        this.clock = clock;
        this.maxPerSecond = properties.maxPerSecond();
    }

    /** @return true if this claim may be processed, false if the player is over the limit */
    public boolean tryAcquire(String username) {
        long second = clock.millis() / 1000;
        Window window = windows.compute(username.toLowerCase(Locale.ROOT), (key, current) ->
                current == null || current.second() != second
                        ? new Window(second, 1)
                        : new Window(second, current.count() + 1));
        return window.count() <= maxPerSecond;
    }
}
