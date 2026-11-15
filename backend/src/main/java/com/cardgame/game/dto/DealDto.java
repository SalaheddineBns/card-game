package com.cardgame.game.dto;

import java.util.List;

public record DealDto(int dealVersion, List<PlayerCardDto> playerCards, CardDto centerCard) {
}
