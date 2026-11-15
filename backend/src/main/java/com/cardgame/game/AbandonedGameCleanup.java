package com.cardgame.game;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * D25: live game state does not survive a restart, so games still IN_PROGRESS from before this boot are
 * marked ABANDONED. The cutoff is taken at construction, before the server accepts traffic, so games
 * started after boot are never touched.
 */
@Component
public class AbandonedGameCleanup {

    private static final Logger log = LoggerFactory.getLogger(AbandonedGameCleanup.class);

    private final GameRecorder recorder;
    private final Instant cutoff;

    public AbandonedGameCleanup(GameRecorder recorder, Clock clock) {
        this.recorder = recorder;
        this.cutoff = clock.instant();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void abandonGamesFromBeforeStartup() {
        int count = recorder.abandonInProgressStartedBefore(cutoff);
        log.info("Marked {} game(s) left in progress before {} as ABANDONED", count, cutoff);
    }

    Instant cutoff() {
        return cutoff;
    }
}
