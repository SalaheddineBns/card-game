package com.cardgame.auth.dto;

import com.cardgame.player.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration form (D18 username/password rules, D11 optional gender). Password confirmation is checked
 * by the client only.
 */
public record RegisterRequest(
        @Schema(example = "alice", description = "3-20 characters: letters, digits, '_' or '-'. Unique ignoring case.")
        @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
        @Size(min = 3, max = 20, message = "Le nom d'utilisateur doit contenir entre 3 et 20 caractères.")
        @Pattern(regexp = "^[A-Za-z0-9_-]*$",
                message = "Le nom d'utilisateur ne peut contenir que des lettres, des chiffres, « _ » et « - ».")
        String username,

        @Schema(example = "password123", description = "8-72 characters", format = "password")
        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères.")
        String password,

        @Schema(example = "25", minimum = "1")
        @NotNull(message = "L'âge est obligatoire.") @Min(value = 1, message = "L'âge doit être au moins 1.")
        Integer age,

        @Schema(nullable = true, description = "Optional")
        Gender gender) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", age=" + age + ", gender=" + gender + "]";
    }
}
