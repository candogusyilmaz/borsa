package dev.canverse.stocks.ledger.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.ledger.application.model.ReconciliationView;
import dev.canverse.stocks.ledger.domain.ReconciliationLifecycleStatus;
import dev.canverse.stocks.ledger.domain.ReconciliationResolution;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReconciliationResponse(
        @NotNull UUID id,
        @NotNull UUID accountId,
        @NotNull UUID cashPocketId,
        @NotNull String currency,
        @NotNull String statementReference,
        @NotNull Instant statementOpeningAt,
        @NotNull Instant statementClosingAt,
        @NotNull String statementOpeningBalance,
        @NotNull String statementClosingBalance,
        @NotNull String ledgerOpeningBalance,
        @NotNull String ledgerClosingBalanceBeforeAdjustment,
        @NotNull String openingDifference,
        @NotNull String periodNetPostedAmount,
        @NotNull String closingDifference,
        String adjustmentAmount,
        long periodPostingCount,
        long totalPostingCountThroughClosing,
        @NotNull ReconciliationResolution resolution,
        UUID adjustmentActivityId,
        String adjustmentReason,
        UUID supersedesReconciliationId,
        @NotNull ReconciliationLifecycleStatus lifecycleStatus,
        @NotNull String sourceKind,
        @NotNull Instant createdAt) {

    public static ReconciliationResponse from(ReconciliationView view) {
        return new ReconciliationResponse(
                view.id(),
                view.accountId(),
                view.cashPocketId(),
                view.currencyCode(),
                view.statementReference(),
                view.statementOpeningAt(),
                view.statementClosingAt(),
                view.statementOpeningBalance(),
                view.statementClosingBalance(),
                view.ledgerOpeningBalance(),
                view.ledgerClosingBalanceBeforeAdjustment(),
                view.openingDifference(),
                view.periodNetPostedAmount(),
                view.closingDifference(),
                view.adjustmentAmount(),
                view.periodPostingCount(),
                view.totalPostingCountThroughClosing(),
                view.resolution(),
                view.adjustmentActivityId(),
                view.adjustmentReason(),
                view.supersedesReconciliationId(),
                view.lifecycleStatus(),
                view.sourceKind(),
                view.createdAt());
    }
}
