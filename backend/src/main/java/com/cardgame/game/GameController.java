package com.cardgame.game;

import com.cardgame.common.ErrorResponses;
import com.cardgame.game.dto.GameResultDto;
import com.cardgame.game.dto.GameSnapshotDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// No `produces` condition: a 204 must not answer 406 to `Accept: application/problem+json` (what generated
// clients send when the only documented body is the error). springdoc documents application/json through
// springdoc.default-produces-media-type.
@RestController
@RequestMapping("/api/games")
@Tag(name = "games", description = "Live game snapshot and result")
public class GameController {

    private final GameService games;

    public GameController(GameService games) {
        this.games = games;
    }

    @GetMapping("/current")
    @Operation(operationId = "getCurrentGame", summary = "My live game, if any (rejoin after a reload, D17)")
    @ApiResponse(responseCode = "200", description = "The caller is in a live game")
    @ApiResponse(responseCode = "204", description = "The caller is not in a live game", content = @Content)
    @ErrorResponses({401})
    public ResponseEntity<GameSnapshotDto> current(Principal principal) {
        return games.currentGameOf(principal.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getGame", summary = "Live snapshot of a game",
            description = "Then follow /topic/game/{id}. 409 when the game is finished (use the result), "
                    + "410 when it was interrupted by a server restart (D25).")
    @ErrorResponses({401, 403, 404, 409, 410})
    public GameSnapshotDto snapshot(@PathVariable long id, Principal principal) {
        return games.snapshot(id, principal.getName());
    }

    @GetMapping("/{id}/result")
    @Operation(operationId = "getGameResult", summary = "Result of a finished game",
            description = "409 while the game is still in progress, 410 when it was interrupted (D25).")
    @ErrorResponses({401, 403, 404, 409, 410})
    public GameResultDto result(@PathVariable long id, Principal principal) {
        return games.result(id, principal.getName());
    }
}
