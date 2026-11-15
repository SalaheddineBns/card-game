package com.cardgame.player.dto;

import com.cardgame.player.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

/** Profile page (row 5). No click ratios (D12), never the password. */
public record PlayerProfileDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "alice") String username,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "25") int age,
        @Schema(nullable = true) Gender gender,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "4") int gamesPlayed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2") int victories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "3.0") double averageScore) {
}
