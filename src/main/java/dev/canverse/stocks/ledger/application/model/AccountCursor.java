package dev.canverse.stocks.ledger.application.model;

import java.util.Objects;
import java.util.UUID;

public record AccountCursor(String filterDigest, String nameNormalized, UUID accountId) {
    public AccountCursor(String nameNormalized, UUID accountId) {
        this("", nameNormalized, accountId);
    }

    public AccountCursor {
        Objects.requireNonNull(filterDigest, "filterDigest");
        Objects.requireNonNull(nameNormalized, "nameNormalized");
        Objects.requireNonNull(accountId, "accountId");
    }
}
