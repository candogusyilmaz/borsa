package dev.canverse.stocks.investing.application.model;

import dev.canverse.stocks.investing.domain.CalculationPolicy;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.ProjectionStatus;
import dev.canverse.stocks.reference.domain.InstrumentType;
import java.time.Instant;
import java.util.UUID;

public record PositionReadModel(UUID accountId, String accountName, UUID instrumentId, String instrumentSymbol, String instrumentName,
        InstrumentType instrumentType, String currency, FinancialAmount quantity, FinancialAmount remainingBasis, FinancialAmount realizedEconomicPnl,
        CalculationPolicy calculationPolicy, ProjectionStatus projectionStatus, Instant asOf, UUID inputWatermarkActivityId, Instant lastSuccessfulBuildAt,
        Instant staleFrom, long version) {}
