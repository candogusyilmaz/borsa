package dev.canverse.stocks.ledger.application.model;

import dev.canverse.stocks.ledger.domain.ReconciliationLifecycleStatus;
import dev.canverse.stocks.ledger.domain.ReconciliationResolution;
import java.time.Instant;
import java.util.UUID;

public record LastReconciliationSummaryView(UUID reconciliationId, Instant statementClosingAt, String statementClosingBalance,
        ReconciliationResolution resolution, ReconciliationLifecycleStatus lifecycleStatus, Instant createdAt) {}
