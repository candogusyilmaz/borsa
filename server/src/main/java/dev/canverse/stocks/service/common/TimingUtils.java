package dev.canverse.stocks.service.common;

import java.util.concurrent.TimeUnit;

public final class TimingUtils {

    private TimingUtils() {}

    public static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
