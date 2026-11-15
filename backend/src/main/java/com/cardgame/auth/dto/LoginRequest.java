package com.cardgame.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "alice")
        @NotBlank(message = "Le nom d'utilisateur est obligatoire.") @Size(max = 20) String username,
        @Schema(example = "password123", format = "password")
        @NotBlank(message = "Le mot de passe est obligatoire.") @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}
