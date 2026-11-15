package com.cardgame.presence;

import com.cardgame.common.ErrorResponses;
import com.cardgame.presence.dto.OnlinePlayerDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "players")
public class PresenceController {

    private final PresenceService presence;

    public PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @GetMapping(path = "/api/players/online", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getOnlinePlayers", summary = "Online players",
            description = "Players with at least one STOMP connection, without the caller, sorted by name. "
                    + "Then follow /topic/presence.")
    @ErrorResponses({401})
    public List<OnlinePlayerDto> onlinePlayers(Principal principal) {
        return presence.onlinePlayers(principal.getName());
    }
}
