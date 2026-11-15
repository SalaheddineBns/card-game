package com.cardgame;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app against a throwaway Testcontainers PostgreSQL (no compose needed), with the {@code local}
 * profile: dev seed alice/bob/carol (password123) and non-secure cookies for plain http.
 * Start with {@code ./mvnw spring-boot:test-run}.
 */
public class TestCardGameApplication {

    public static void main(String[] args) {
        SpringApplication.from(CardGameApplication::main).with(TestcontainersConfiguration.class).withAdditionalProfiles("local").run(args);
    }
}
