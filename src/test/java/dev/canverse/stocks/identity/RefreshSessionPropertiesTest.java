package dev.canverse.stocks.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.identity.configuration.RefreshSessionConfiguration;
import dev.canverse.stocks.identity.configuration.RefreshSessionProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RefreshSessionPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(RefreshSessionConfiguration.class);

    @Test
    void absentLifetimeBindsToThirtyDayDefault() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBean(RefreshSessionProperties.class).lifetime())
                    .isEqualTo(Duration.ofDays(30));
        });
    }

    @Test
    void nullAndNonPositiveLifetimesAreRejected() {
        assertThatThrownBy(() -> new RefreshSessionProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lifetime must be positive");

        for (var invalidValue : List.of("0s", "-1s")) {
            contextRunner
                    .withPropertyValues("stocks.identity.refresh-session.lifetime=" + invalidValue)
                    .run(context -> assertThat(rootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage("lifetime must be positive"));
        }
    }

    private Throwable rootCause(Throwable throwable) {
        assertThat(throwable).isNotNull();
        var rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
