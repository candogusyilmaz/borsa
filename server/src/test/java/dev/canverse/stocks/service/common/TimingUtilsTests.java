package dev.canverse.stocks.service.common;

import static dev.canverse.stocks.service.common.TimingUtils.elapsedMs;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TimingUtilsTests {

    @Test
    void computesElapsedMillisecondsFromMonotonicStartTime() {
        long startNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(25);

        long elapsed = elapsedMs(startNanos);

        assertThat(elapsed).isGreaterThanOrEqualTo(25L).isLessThan(1_000L);
    }

    @Test
    void returnsZeroOrGreaterForCurrentNanoTime() {
        long startNanos = System.nanoTime();

        assertThat(elapsedMs(startNanos)).isGreaterThanOrEqualTo(0L);
    }
}
