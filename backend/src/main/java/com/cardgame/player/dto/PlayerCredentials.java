package com.cardgame.player.dto;

/** What authentication needs to know about a player. Internal: never returned by a controller. */
public record PlayerCredentials(String username, String passwordHash) {

    @Override
    public String toString() {
        return "PlayerCredentials[username=" + username + "]";
    }
}
