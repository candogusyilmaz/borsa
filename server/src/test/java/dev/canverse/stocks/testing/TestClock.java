package dev.canverse.stocks.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Mutable deterministic clock for the serial Spring integration-test suite. */
public class TestClock extends Clock {

    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-08-15T12:00:00Z");

    private final AtomicReference<Instant> currentInstant;
    private final ZoneId zone;

    public TestClock() {
        this(new AtomicReference<>(DEFAULT_INSTANT), ZoneOffset.UTC);
    }

    protected TestClock(AtomicReference<Instant> currentInstant, ZoneId zone) {
        this.currentInstant = currentInstant;
        this.zone = zone;
    }

    public void reset() {
        setInstant(DEFAULT_INSTANT);
    }

    public void setInstant(Instant instant) {
        currentInstant.set(Objects.requireNonNull(instant, "instant"));
    }

    public void set(Instant instant) {
        setInstant(instant);
    }

    public void advance(Duration duration) {
        currentInstant.updateAndGet(instant -> instant.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(currentInstant, zone);
    }

    @Override
    public Instant instant() {
        return currentInstant.get();
    }
}
