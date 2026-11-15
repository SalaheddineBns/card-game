package com.cardgame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cardgame.game.dto.ClaimMessage;
import com.cardgame.game.engine.Claim;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** G10: per-player claim limit, extra claims dropped silently. */
class ClaimRateLimiterTest {

    private static final class StepClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00.100Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final StepClock clock = new StepClock();
    private final ClaimRateLimiter limiter = new ClaimRateLimiter(clock, new ClaimProperties(3));

    @Test
    void allowsTheConfiguredNumberOfClaimsPerSecondAndPlayer() {
        assertThat(IntStream.range(0, 5).mapToObj(i -> limiter.tryAcquire("alice")))
                .containsExactly(true, true, true, false, false);
        // case-insensitive key, other players unaffected
        assertThat(limiter.tryAcquire("ALICE")).isFalse();
        assertThat(limiter.tryAcquire("bob")).isTrue();
    }

    @Test
    void theLimitResetsEverySecond() {
        IntStream.range(0, 4).forEach(i -> limiter.tryAcquire("alice"));

        clock.now = clock.now.plus(Duration.ofMillis(900));

        assertThat(limiter.tryAcquire("alice")).isTrue();
    }

    @Test
    void theMessagingControllerDropsClaimsOverTheLimitWithoutAnswering() {
        GameService games = mock(GameService.class);
        GameMessagingController controller = new GameMessagingController(games, limiter, clock);
        Principal alice = () -> "alice";

        for (int i = 0; i < 6; i++) {
            controller.claim(7L, new ClaimMessage(Claim.BOTH, 1), alice);
        }

        verify(games, times(3)).claim(anyLong(), anyString(), any(), anyInt());
    }
}
