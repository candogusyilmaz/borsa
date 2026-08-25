package dev.canverse.stocks.ledger.web.response;

import dev.canverse.stocks.ledger.application.model.LastReconciliationSummaryView;
import dev.canverse.stocks.ledger.domain.ReconciliationLifecycleStatus;
import dev.canverse.stocks.ledger.domain.ReconciliationResolution;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record LastReconciliationSummaryResponse(
        @NotNull UUID reconciliationId,
        @NotNull Instant statementClosingAt,
        @NotNull String statementClosingBalance,
        @NotNull ReconciliationResolution resolution,
        @NotNull ReconciliationLifecycleStatus lifecycleStatus,
        @NotNull Instant createdAt) {

    public static LastReconciliationSummaryResponse from(LastReconciliationSummaryView view) {
        return new LastReconciliationSummaryResponse(
                view.reconciliationId(),
                view.statementClosingAt(),
                view.statementClosingBalance(),
                view.resolution(),
                view.lifecycleStatus(),
                view.createdAt());
    }
}
