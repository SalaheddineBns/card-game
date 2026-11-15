package com.cardgame.game;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * {@code cardgame.claims.*}.
 *
 * @param maxPerSecond claims accepted per player per second; extra claims are dropped silently (G10)
 */
@Validated
@ConfigurationProperties("cardgame.claims")
public record ClaimProperties(@DefaultValue("10") @Min(1) int maxPerSecond) {
}
