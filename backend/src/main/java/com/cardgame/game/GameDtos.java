package com.cardgame.game;

import com.cardgame.game.dto.CardDto;
import com.cardgame.game.dto.DealDto;
import com.cardgame.game.dto.GameSettingsDto;
import com.cardgame.game.dto.GameSnapshotDto;
import com.cardgame.game.dto.PlayerCardDto;
import com.cardgame.game.dto.PlayerScoreDto;
import com.cardgame.game.engine.Card;
import com.cardgame.game.engine.GameState;
import java.util.List;
import java.util.Map;

/** Engine types → wire DTOs. */
final class GameDtos {

    private GameDtos() {
    }

    static List<PlayerScoreDto> scores(Map<String, Integer> scores) {
        return scores.entrySet().stream().map(e -> new PlayerScoreDto(e.getKey(), e.getValue())).toList();
    }

    static DealDto deal(GameState state) {
        List<PlayerCardDto> cards = state.deal().playerCards().stream()
                .map(pc -> new PlayerCardDto(pc.player(), symbol(pc.card()), pc.card().color().wireValue()))
                .toList();
        Card center = state.deal().centerCard();
        return new DealDto(state.dealVersion(), cards, new CardDto(symbol(center), center.color().wireValue()));
    }

    static GameSnapshotDto snapshot(long gameId, GameState state) {
        return new GameSnapshotDto(gameId, state.players(), GameSettingsDto.from(state.settings()), state.phase(),
                state.currentRound(), state.roundEndsAt(), scores(state.scores()), deal(state));
    }

    private static String symbol(Card card) {
        return String.valueOf(card.symbol());
    }
}
