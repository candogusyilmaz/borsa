package dev.canverse.stocks.ledger.application.model;

import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import java.time.Instant;
import java.util.UUID;

public record FinancialAccountView(
        UUID id,
        String name,
        AccountKind accountKind,
        TrackingMode trackingMode,
        String currencyCode,
        String timeZone,
        NegativeBalancePolicy negativeBalancePolicy,
        String authorizedLimit,
        Instant archivedAt,
        CoverageStatus coverageStatus,
        Instant coverageFrom,
        String sourceKind,
        long version,
        Instant createdAt,
        Instant updatedAt,
        boolean policyBreach) {}
