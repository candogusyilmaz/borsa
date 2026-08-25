package dev.canverse.stocks.ledger.application.model;

import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationPreviewView(
        UUID accountId,
        UUID cashPocketId,
        String currencyCode,
        CoverageStatus coverageStatus,
        Instant coverageFrom,
        String statementReference,
        Instant statementOpeningAt,
        Instant statementClosingAt,
        FinancialAmount statementOpeningBalance,
        FinancialAmount statementClosingBalance,
        FinancialAmount ledgerOpeningBalance,
        FinancialAmount ledgerClosingBalanceBeforeAdjustment,
        FinancialAmount openingDifference,
        FinancialAmount periodNetPostedAmount,
        FinancialAmount closingDifference,
        long periodPostingCount,
        long totalPostingCountThroughClosing,
        long projectionVersion,
        List<String> admissibleResolutions,
        List<String> warnings) {

    public ReconciliationPreviewView {
        admissibleResolutions = List.copyOf(admissibleResolutions);
        warnings = List.copyOf(warnings);
    }
}
