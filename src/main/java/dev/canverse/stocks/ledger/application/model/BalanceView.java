package dev.canverse.stocks.ledger.application.model;

import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.ProjectionStatus;
import java.time.Instant;
import java.util.UUID;

public record BalanceView(
        UUID accountId,
        Instant requestedAsOf,
        Instant actualAsOf,
        String nativeCurrency,
        CoverageStatus coverageStatus,
        Instant coverageFrom,
        String sourceKind,
        ProjectionStatus projectionStatus,
        Instant watermarkRecordedAt,
        UUID watermarkActivityId,
        String ledgerBalance,
        String clearedBalance,
        String cashHeld,
        String liabilityOutstanding,
        String overdraftUsed,
        String creditAvailable,
        boolean policyBreach) {}
