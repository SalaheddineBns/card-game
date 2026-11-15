package com.cardgame.game.dto;

import com.cardgame.game.engine.GameSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;

/** Game settings on the wire (D10). */
public record GameSettingsDto(
        @Schema(example = "3", minimum = "1", maximum = "20", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(value = 1, message = "Le nombre de manches doit être entre 1 et 20.")
        @Max(value = 20, message = "Le nombre de manches doit être entre 1 et 20.")
        int totalRounds,
        @Schema(example = "10", minimum = "5", maximum = "120", description = "Seconds per round",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(value = 5, message = "La durée d'une manche doit être entre 5 et 120 secondes.")
        @Max(value = 120, message = "La durée d'une manche doit être entre 5 et 120 secondes.")
        int roundSeconds) {

    /** @throws IllegalArgumentException outside the D10 limits */
    public GameSettings toSettings() {
        return new GameSettings(totalRounds, Duration.ofSeconds(roundSeconds));
    }

    public static GameSettingsDto from(GameSettings settings) {
        return new GameSettingsDto(settings.totalRounds(), (int) settings.roundDuration().toSeconds());
    }
}
