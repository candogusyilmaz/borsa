package dev.canverse.stocks.ledger.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.ledger.application.model.FinancialAccountView;
import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.CoverageStatus;
import dev.canverse.stocks.ledger.domain.NegativeBalancePolicy;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FinancialAccountResponse(
        @NotNull UUID id,
        @NotNull String name,
        @NotNull AccountKind kind,
        @NotNull TrackingMode trackingMode,
        @NotNull String currency,
        @NotNull String timeZone,
        NegativeBalancePolicy policy,
        String authorizedLimit,
        boolean archived,
        Instant archivedAt,
        @NotNull CoverageStatus cashCoverageStatus,
        Instant coverageFrom,
        @NotNull String sourceKind,
        long version,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        boolean policyBreach) {

    public static FinancialAccountResponse from(FinancialAccountView view) {
        return new FinancialAccountResponse(
                view.id(),
                view.name(),
                view.accountKind(),
                view.trackingMode(),
                view.currencyCode(),
                view.timeZone(),
                view.negativeBalancePolicy(),
                view.authorizedLimit(),
                view.archivedAt() != null,
                view.archivedAt(),
                view.coverageStatus(),
                view.coverageFrom(),
                view.sourceKind(),
                view.version(),
                view.createdAt(),
                view.updatedAt(),
                view.policyBreach());
    }
}
