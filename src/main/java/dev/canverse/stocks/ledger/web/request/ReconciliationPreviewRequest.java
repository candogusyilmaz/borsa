package dev.canverse.stocks.ledger.web.request;

import dev.canverse.stocks.platform.error.ValidationErrors;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ReconciliationPreviewRequest(
        @NotBlank @Size(max = 200) String statementReference,
        @NotNull Instant statementOpeningAt,
        @NotNull Instant statementClosingAt,
        @NotBlank String statementOpeningBalance,
        @NotBlank String statementClosingBalance) {

    public void validate() {
        if (statementOpeningAt != null
                && statementClosingAt != null
                && !statementOpeningAt.isBefore(statementClosingAt)) {
            throw ValidationErrors.invalidField(
                    "statementOpeningAt",
                    "error.fields.ledger.invalid_reconciliation_period",
                    "The statement opening instant must precede the closing instant.");
        }
    }
}
