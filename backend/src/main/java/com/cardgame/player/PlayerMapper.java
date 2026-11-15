package com.cardgame.player;

import com.cardgame.player.dto.PlayerCredentials;
import com.cardgame.player.dto.PlayerDto;
import com.cardgame.player.dto.PlayerProfileDto;
import org.mapstruct.Mapper;

@Mapper
public interface PlayerMapper {

    PlayerDto toDto(Player player);

    PlayerCredentials toCredentials(Player player);

    PlayerProfileDto toProfile(Player player);
}
