package com.cardgame.player.dto;

import com.cardgame.player.Gender;

/** Data needed to create a player; the password is already hashed. */
public record NewPlayer(String username, String passwordHash, int age, Gender gender) {
}
