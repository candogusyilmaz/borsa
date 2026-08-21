package dev.canverse.stocks.ledger.application;

import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.platform.error.ValidationErrors;

/** Translates HTTP amount text into the exact financial value object. */
final class LedgerAmountParser {

    private LedgerAmountParser() {}

    static FinancialAmount exact(String text, String field) {
        try {
            return FinancialAmount.parse(text);
        } catch (IllegalArgumentException exception) {
            throw ValidationErrors.invalidField(
                    field, "error.fields.ledger.invalid_amount", "The amount must be an exact plain decimal.");
        }
    }

    static FinancialAmount optional(String text, String field) {
        return text == null ? null : exact(text, field);
    }

    static FinancialAmount positive(String text, String field) {
        try {
            var amount = FinancialAmount.parse(text);
            if (!amount.isPositive()) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return amount;
        } catch (IllegalArgumentException exception) {
            throw ValidationErrors.invalidField(
                    field, "error.fields.ledger.invalid_amount", "The amount must be positive and exact.");
        }
    }
}
