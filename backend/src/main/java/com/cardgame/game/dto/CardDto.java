package com.cardgame.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A card on the wire: {@code symbol} like {@code "A"} (11 letters, no G - D1), {@code color} like {@code "red"}. */
public record CardDto(
        @Schema(allowableValues = {"A", "B", "C", "D", "E", "F", "H", "I", "J", "K", "L"}) String symbol,
        @Schema(allowableValues = {"red", "blue", "green"}) String color) {
}
