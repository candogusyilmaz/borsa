package dev.canverse.stocks.ledger.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReconciliationCursor(String filterDigest, Instant statementClosingAt, UUID reconciliationId) {

    public ReconciliationCursor {
        Objects.requireNonNull(filterDigest, "filterDigest");
        Objects.requireNonNull(statementClosingAt, "statementClosingAt");
        Objects.requireNonNull(reconciliationId, "reconciliationId");
    }
}
