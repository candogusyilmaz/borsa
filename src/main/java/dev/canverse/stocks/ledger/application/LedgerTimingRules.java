package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.platform.error.ValidationErrors;
import java.time.Instant;
import java.util.Objects;

/** Shared temporal rules for financial facts and balance reads. */
final class LedgerTimingRules {

    private LedgerTimingRules() {}

    static void rejectFuture(Instant candidate, Instant observedAt, String field) {
        if (Objects.requireNonNull(candidate, field).isAfter(observedAt)) {
            throw ValidationErrors.invalidField(
                    field, "error.fields.ledger.future_time", "Financial facts cannot be effective in the future.");
        }
    }
}
