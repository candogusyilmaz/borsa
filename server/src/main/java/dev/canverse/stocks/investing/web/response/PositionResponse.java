package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.investing.application.model.PositionReadModel;
import dev.canverse.stocks.investing.domain.CalculationPolicy;
import dev.canverse.stocks.ledger.domain.ProjectionStatus;
import dev.canverse.stocks.reference.domain.InstrumentType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PositionResponse(
        @NotNull UUID accountId,
        @NotNull String accountName,
        @NotNull UUID instrumentId,
        @NotNull String instrumentSymbol,
        @NotNull String instrumentName,
        @NotNull InstrumentType instrumentType,
        @NotNull String currency,
        @NotNull String quantity,
        @NotNull String remainingEconomicBasis,
        @NotNull String cumulativeRealizedEconomicPnl,
        @NotNull CalculationPolicy calculationPolicy,
        @NotNull ProjectionStatus projectionStatus,
        @NotNull Instant asOf,
        @NotNull UUID inputWatermarkActivityId,
        @NotNull Instant lastSuccessfulBuildAt,
        Instant staleFrom,
        @NotNull Long version
) {

    public static PositionResponse from(PositionReadModel model) {
        return new PositionResponse(model.accountId(), model.accountName(), model.instrumentId(), model.instrumentSymbol(), model.instrumentName(),
                model.instrumentType(), model.currency(), model.quantity().canonical(), model.remainingBasis().canonical(),
                model.realizedEconomicPnl().canonical(), model.calculationPolicy(), model.projectionStatus(), model.asOf(), model.inputWatermarkActivityId(),
                model.lastSuccessfulBuildAt(), model.staleFrom(), model.version());
    }
}
