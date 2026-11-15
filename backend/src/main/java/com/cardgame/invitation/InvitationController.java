package com.cardgame.invitation;

import com.cardgame.common.ErrorResponses;
import com.cardgame.invitation.dto.CreateInvitationRequest;
import com.cardgame.invitation.dto.InvitationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// No `produces` condition: a 204 must not answer 406 to `Accept: application/problem+json` (what generated
// clients send when the only documented body is the error). springdoc documents application/json through
// springdoc.default-produces-media-type.
@RestController
@RequestMapping("/api/invitations")
@Tag(name = "invitations", description = "Game setup: invite, accept, decline (D7)")
public class InvitationController {

    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createInvitation", summary = "Invite online players",
            description = "Each invitee receives InvitationReceived on /user/queue/invitations. Expires after 60 s.")
    @ApiResponse(responseCode = "201", description = "Invitation created (no Location header, D24)")
    @ErrorResponses({400, 401, 403, 409})
    public InvitationDto create(@Valid @RequestBody CreateInvitationRequest request, Principal principal) {
        return invitations.create(principal.getName(), request);
    }

    @PostMapping("/{id}/accept")
    @Operation(operationId = "acceptInvitation", summary = "Accept an invitation",
            description = "When every invitee has accepted, the game starts (GameStarted on /user/queue/games).")
    @ErrorResponses({401, 403, 404, 409})
    public InvitationDto accept(@PathVariable long id, Principal principal) {
        return invitations.accept(id, principal.getName());
    }

    @PostMapping("/{id}/decline")
    @Operation(operationId = "declineInvitation", summary = "Decline an invitation",
            description = "Cancels the invitation for everyone.")
    @ErrorResponses({401, 403, 404, 409})
    public InvitationDto decline(@PathVariable long id, Principal principal) {
        return invitations.decline(id, principal.getName());
    }

    @GetMapping("/pending")
    @Operation(operationId = "getPendingInvitations", summary = "My pending invitations (as inviter or invitee)")
    @ErrorResponses({401})
    public List<InvitationDto> pending(Principal principal) {
        return invitations.pendingFor(principal.getName());
    }
}
