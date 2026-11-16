package com.cardgame.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

/**
 * {@code cardgame.demo-reset.*}: the daily reset of the public demo (D29). Off by default; the prod profile turns
 * it on.
 *
 * @param enabled schedule the daily reset and seed the demo players at startup when the database is empty
 * @param cron Spring cron (seconds first), default every day at 04:00
 * @param zone time zone of {@code cron}
 * @param demoPassword password of every demo player (8-72 characters, D23), env
 *     {@code CARDGAME_DEMO_RESET_DEMO_PASSWORD}
 * @param demoUsers usernames recreated by the reset (D18 rules)
 */
@Validated
@ConfigurationProperties("cardgame.demo-reset")
public record DemoResetProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("0 0 4 * * *") @NotBlank String cron,
        @DefaultValue("Europe/Paris") @NotBlank String zone,
        @DefaultValue("demo1234") @Size(min = 8, max = 72) String demoPassword,
        @DefaultValue({"alice", "bob", "carol"}) @NotEmpty List<@NotBlank String> demoUsers) {

    public DemoResetProperties {
        // fail at startup, not at 04:00
        if (cron != null && !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("cardgame.demo-reset.cron is not a valid cron expression: " + cron);
        }
        if (zone != null) {
            ZoneId.of(zone);
        }
        demoUsers = demoUsers == null ? List.of() : List.copyOf(demoUsers);
    }
}
