package com.cardgame.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Configuration;

class DemoResetPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DemoResetProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void defaultsAreOffAndFourInTheMorningParisTime() {
        runner.run(context -> {
            DemoResetProperties properties = context.getBean(DemoResetProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.cron()).isEqualTo("0 0 4 * * *");
            assertThat(properties.zone()).isEqualTo("Europe/Paris");
            assertThat(properties.demoPassword()).isEqualTo("demo1234");
            assertThat(properties.demoUsers()).containsExactly("alice", "bob", "carol");
        });
    }

    @Test
    void everythingCanBeOverridden() {
        runner.withPropertyValues(
                        "cardgame.demo-reset.enabled=true",
                        "cardgame.demo-reset.cron=0 30 5 * * MON-FRI",
                        "cardgame.demo-reset.zone=UTC",
                        "cardgame.demo-reset.demo-password=another-pass",
                        "cardgame.demo-reset.demo-users=dora,eve")
                .run(context -> {
                    DemoResetProperties properties = context.getBean(DemoResetProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.cron()).isEqualTo("0 30 5 * * MON-FRI");
                    assertThat(properties.zone()).isEqualTo("UTC");
                    assertThat(properties.demoPassword()).isEqualTo("another-pass");
                    assertThat(properties.demoUsers()).isEqualTo(List.of("dora", "eve"));
                });
    }

    @Test
    void anInvalidCronZoneOrPasswordFailsAtStartup() {
        runner.withPropertyValues("cardgame.demo-reset.cron=every day at four")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("cardgame.demo-reset.zone=Mars/Olympus")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("cardgame.demo-reset.demo-password=short")
                .run(context -> assertThat(context).hasFailed());
    }
}
