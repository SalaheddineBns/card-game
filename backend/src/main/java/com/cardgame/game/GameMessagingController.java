package com.cardgame.game;

import com.cardgame.common.DomainException;
import com.cardgame.game.dto.ClaimMessage;
import com.cardgame.realtime.Destinations;
import com.cardgame.realtime.ErrorEvent;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/** STOMP commands of a game. Thin: the service publishes the results. */
@Controller
public class GameMessagingController {

    private static final Logger log = LoggerFactory.getLogger(GameMessagingController.class);

    private final GameService games;
    private final ClaimRateLimiter rateLimiter;
    private final Clock clock;

    public GameMessagingController(GameService games, ClaimRateLimiter rateLimiter, Clock clock) {
        this.games = games;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /** SEND /app/game/{gameId}/claim. Claims over the per-player rate limit are dropped silently (G10). */
    @MessageMapping("/game/{gameId}/claim")
    public void claim(@DestinationVariable long gameId, @Valid @Payload ClaimMessage message, Principal principal) {
        if (!rateLimiter.tryAcquire(principal.getName())) {
            log.debug("Claim from {} dropped (rate limit)", principal.getName());
            return;
        }
        games.claim(gameId, principal.getName(), message.claim(), message.dealVersion());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = Destinations.ERRORS_QUEUE, broadcast = false)
    public ErrorEvent onDomainError(DomainException e) {
        return new ErrorEvent(e.status().value(), e.title(), e.getMessage(), clock.instant());
    }

    @MessageExceptionHandler({MethodArgumentNotValidException.class, MessageConversionException.class})
    @SendToUser(destinations = Destinations.ERRORS_QUEUE, broadcast = false)
    public ErrorEvent onInvalidMessage(Exception e) {
        log.debug("Invalid STOMP message", e);
        return new ErrorEvent(400, "Message invalide",
                "Le message est illisible ou incomplet (claim et dealVersion attendus).", clock.instant());
    }
}
