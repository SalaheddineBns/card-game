package com.cardgame.player;

import com.cardgame.common.ErrorResponses;
import com.cardgame.player.dto.PlayerProfileDto;
import com.cardgame.player.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/players", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "players", description = "Profile and lobby presence")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping("/me")
    @Operation(operationId = "getMyProfile", summary = "My profile with stats")
    @ErrorResponses({401})
    public PlayerProfileDto me(Principal principal) {
        return playerService.getProfile(principal.getName());
    }

    @PutMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateMyProfile", summary = "Update my age and gender")
    @ErrorResponses({400, 401, 403})
    public PlayerProfileDto update(@Valid @RequestBody UpdateProfileRequest request, Principal principal) {
        return playerService.updateProfile(principal.getName(), request);
    }
}
