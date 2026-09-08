package dev.canverse.stocks.ledger.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.ledger.application.model.ReconciliationPreviewView;
import dev.canverse.stocks.ledger.domain.CoverageStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReconciliationPreviewResponse(@NotNull UUID accountId, @NotNull UUID cashPocketId, @NotNull String currency,
        @NotNull CoverageStatus coverageStatus, @NotNull Instant coverageFrom, @NotNull String statementReference, @NotNull Instant statementOpeningAt,
        @NotNull Instant statementClosingAt, @NotNull String statementOpeningBalance, @NotNull String statementClosingBalance,
        @NotNull String ledgerOpeningBalance, @NotNull String ledgerClosingBalanceBeforeAdjustment, @NotNull String openingDifference,
        @NotNull String periodNetPostedAmount, @NotNull String closingDifference, long periodPostingCount, long totalPostingCountThroughClosing,
        long projectionVersion, @NotNull List<String> admissibleResolutions, @NotNull List<String> warnings) {

    public ReconciliationPreviewResponse {
        admissibleResolutions = List.copyOf(admissibleResolutions);
        warnings = List.copyOf(warnings);
    }

    public static ReconciliationPreviewResponse from(ReconciliationPreviewView view) {
        return new ReconciliationPreviewResponse(view.accountId(), view.cashPocketId(), view.currencyCode(), view.coverageStatus(), view.coverageFrom(),
                view.statementReference(), view.statementOpeningAt(), view.statementClosingAt(), view.statementOpeningBalance().canonical(),
                view.statementClosingBalance().canonical(), view.ledgerOpeningBalance().canonical(), view.ledgerClosingBalanceBeforeAdjustment().canonical(),
                view.openingDifference().canonical(), view.periodNetPostedAmount().canonical(), view.closingDifference().canonical(), view.periodPostingCount(),
                view.totalPostingCountThroughClosing(), view.projectionVersion(), view.admissibleResolutions(), view.warnings());
    }
}
