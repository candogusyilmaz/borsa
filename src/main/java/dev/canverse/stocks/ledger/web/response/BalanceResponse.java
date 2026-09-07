package dev.canverse.stocks.ledger.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.ledger.application.model.BalanceView;
import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.ProjectionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BalanceResponse(@NotNull UUID accountId, @NotNull Instant requestedAsOf, @NotNull Instant actualAsOf, @NotNull String nativeCurrency,
        @NotNull CoverageStatus coverageStatus, Instant coverageFrom, @NotNull String sourceKind, @NotNull ProjectionStatus projectionStatus,
        Instant watermarkRecordedAt, UUID watermarkActivityId, String ledgerBalance, String clearedBalance, String cashHeld, String liabilityOutstanding,
        String overdraftUsed, String creditAvailable, boolean policyBreach, LastReconciliationSummaryResponse lastReconciliation) {

    public static BalanceResponse from(BalanceView view) {
        return new BalanceResponse(view.accountId(), view.requestedAsOf(), view.actualAsOf(), view.nativeCurrency(), view.coverageStatus(), view.coverageFrom(),
                view.sourceKind(), view.projectionStatus(), view.watermarkRecordedAt(), view.watermarkActivityId(), view.ledgerBalance(), view.clearedBalance(),
                view.cashHeld(), view.liabilityOutstanding(), view.overdraftUsed(), view.creditAvailable(), view.policyBreach(),
                view.lastReconciliation() == null ? null : LastReconciliationSummaryResponse.from(view.lastReconciliation()));
    }
}
