package dev.canverse.stocks.ledger.application.model;

import dev.canverse.stocks.ledger.domain.ReconciliationLifecycleStatus;
import dev.canverse.stocks.ledger.domain.ReconciliationResolution;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationView(
        UUID id,
        UUID accountId,
        UUID cashPocketId,
        String currencyCode,
        String statementReference,
        Instant statementOpeningAt,
        Instant statementClosingAt,
        String statementOpeningBalance,
        String statementClosingBalance,
        String ledgerOpeningBalance,
        String ledgerClosingBalanceBeforeAdjustment,
        String openingDifference,
        String periodNetPostedAmount,
        String closingDifference,
        String adjustmentAmount,
        long periodPostingCount,
        long totalPostingCountThroughClosing,
        ReconciliationResolution resolution,
        UUID adjustmentActivityId,
        String adjustmentReason,
        UUID supersedesReconciliationId,
        ReconciliationLifecycleStatus lifecycleStatus,
        String sourceKind,
        Instant createdAt) {}
