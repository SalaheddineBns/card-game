package com.cardgame.presence.dto;

import com.cardgame.presence.PlayerStatus;

public record OnlinePlayerDto(String username, PlayerStatus status) {
}
