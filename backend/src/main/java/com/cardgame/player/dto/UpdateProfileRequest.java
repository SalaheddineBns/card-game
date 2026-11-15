package com.cardgame.player.dto;

import com.cardgame.player.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Row 6: only age and gender can change (username read-only, password out of scope - D19). */
public record UpdateProfileRequest(
        @Schema(example = "26", minimum = "1")
        @NotNull(message = "L'âge est obligatoire.") @Min(value = 1, message = "L'âge doit être au moins 1.")
        Integer age,
        @Schema(nullable = true, description = "Optional; null clears it")
        Gender gender) {
}
