package dev.canverse.stocks.identity.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SessionCursor(Instant createdAt, UUID familyId) {

    public SessionCursor {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(familyId, "familyId");
    }
}
