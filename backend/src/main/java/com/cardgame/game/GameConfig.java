package com.cardgame.game;

import com.cardgame.game.engine.GameEngine;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClaimProperties.class)
class GameConfig {

    /** Thread-safe: several games deal concurrently. */
    @Bean
    RandomGenerator gameRandom() {
        return new SecureRandom();
    }

    @Bean
    GameEngine gameEngine(RandomGenerator gameRandom, Clock clock) {
        return new GameEngine(gameRandom, clock);
    }

    /** Round timers and invitation expiry (virtual threads). */
    @Bean
    SimpleAsyncTaskScheduler gameTaskScheduler(Clock clock) {
        SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setThreadNamePrefix("game-timer-");
        scheduler.setVirtualThreads(true);
        scheduler.setClock(clock);
        return scheduler;
    }
}
