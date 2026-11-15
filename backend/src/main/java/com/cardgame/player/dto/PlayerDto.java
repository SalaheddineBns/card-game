package com.cardgame.player.dto;

import com.cardgame.player.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

/** Public view of a player account. Never contains the password hash. */
public record PlayerDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "alice") String username,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "25") int age,
        @Schema(nullable = true, description = "Optional (D11)") Gender gender) {
}
