package dev.canverse.stocks.ledger.web.request;

import dev.canverse.stocks.platform.error.ValidationErrors;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationCommitRequest(@NotBlank @Size(max = 200) String statementReference, @NotNull Instant statementOpeningAt,
        @NotNull Instant statementClosingAt, @NotBlank String statementOpeningBalance, @NotBlank String statementClosingBalance, @NotNull UUID clientRequestId,
        @NotNull @PositiveOrZero Long expectedBalanceVersion, @NotNull ReconciliationAction resolution, @Size(max = 500) String adjustmentReason) {

    public void validate() {
        validatePeriod();
        validateReason();
    }

    private void validatePeriod() {
        if (statementOpeningAt != null && statementClosingAt != null && !statementOpeningAt.isBefore(statementClosingAt)) {
            throw ValidationErrors.invalidField("statementOpeningAt", "error.fields.ledger.invalid_reconciliation_period",
                    "The statement opening instant must precede the closing instant.");
        }
    }

    private void validateReason() {
        if (resolution == ReconciliationAction.CREATE_ADJUSTMENT) {
            if (adjustmentReason == null || adjustmentReason.trim().isEmpty()) {
                throw ValidationErrors.invalidField("adjustmentReason", "error.fields.ledger.adjustment_reason_required",
                        "An adjustment reason must contain 1 to 500 characters.");
            }
        } else if (resolution == ReconciliationAction.CONFIRM_BALANCED && adjustmentReason != null) {
            throw ValidationErrors.invalidField("adjustmentReason", "error.fields.ledger.adjustment_reason_prohibited",
                    "An adjustment reason is only valid when creating an adjustment.");
        }
    }
}
