package com.cardgame.demo;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * D29: daily demo reset and the first seed, only when {@code cardgame.demo-reset.enabled=true} (prod profile).
 * {@code @Scheduled} methods run on this class's own single-thread scheduler, never on the game timers'
 * scheduler or the STOMP heartbeat scheduler.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "cardgame.demo-reset", name = "enabled", havingValue = "true")
public class DemoResetJob implements SchedulingConfigurer, DisposableBean {

    private final DemoResetService demoReset;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    public DemoResetJob(DemoResetService demoReset) {
        this.demoReset = demoReset;
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("demo-reset-");
        scheduler.initialize();
    }

    @Scheduled(cron = "${cardgame.demo-reset.cron:0 0 4 * * *}", zone = "${cardgame.demo-reset.zone:Europe/Paris}")
    public void dailyReset() {
        demoReset.reset();
    }

    /** A fresh deploy gets the demo players before the first scheduled reset. */
    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        demoReset.seedIfEmpty();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduler);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }
}
