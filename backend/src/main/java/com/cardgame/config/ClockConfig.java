package com.cardgame.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** The single time source of the services and the game engine. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
