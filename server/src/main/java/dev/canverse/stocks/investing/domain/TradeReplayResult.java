package dev.canverse.stocks.investing.domain;

import dev.canverse.stocks.ledger.domain.FinancialAmount;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TradeReplayResult(PositionState state, Map<UUID, Disposal> disposals, Instant asOf, UUID watermarkActivityId) {

    public TradeReplayResult {
        disposals = Map.copyOf(disposals);
    }

    public record Disposal(FinancialAmount allocatedBasis, FinancialAmount realizedEconomicPnl) {}
}
