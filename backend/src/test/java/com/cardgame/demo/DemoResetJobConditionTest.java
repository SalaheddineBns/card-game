package com.cardgame.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The scheduled job (and the startup seed) exist only when cardgame.demo-reset.enabled=true. */
class DemoResetJobConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DemoResetService.class, () -> mock(DemoResetService.class))
            .withUserConfiguration(DemoResetJob.class);

    @Test
    void absentByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoResetJob.class));
    }

    @Test
    void absentWhenDisabled() {
        runner.withPropertyValues("cardgame.demo-reset.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoResetJob.class));
    }

    @Test
    void presentWhenEnabled() {
        runner.withPropertyValues("cardgame.demo-reset.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DemoResetJob.class));
    }
}
