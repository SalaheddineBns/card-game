package com.cardgame.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlayerCardDto(
        String player,
        @Schema(allowableValues = {"A", "B", "C", "D", "E", "F", "H", "I", "J", "K", "L"}) String symbol,
        @Schema(allowableValues = {"red", "blue", "green"}) String color) {
}
