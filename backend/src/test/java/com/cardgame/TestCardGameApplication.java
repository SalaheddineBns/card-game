package com.cardgame;

import org.springframework.boot.SpringApplication;

/** Runs the app against a throwaway Testcontainers PostgreSQL (no compose needed). */
public class TestCardGameApplication {

    public static void main(String[] args) {
        SpringApplication.from(CardGameApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
