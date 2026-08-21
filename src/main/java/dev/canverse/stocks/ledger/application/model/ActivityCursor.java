package dev.canverse.stocks.ledger.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ActivityCursor(String filterDigest, Instant recordedAt, UUID activityId) {
    public ActivityCursor(Instant recordedAt, UUID activityId) {
        this("", recordedAt, activityId);
    }

    public ActivityCursor {
        Objects.requireNonNull(filterDigest, "filterDigest");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(activityId, "activityId");
    }
}
