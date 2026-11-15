package com.cardgame.game;

import org.springframework.data.jpa.repository.JpaRepository;

interface GameRepository extends JpaRepository<Game, Long> {
}
