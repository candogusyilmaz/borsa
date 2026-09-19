package dev.canverse.stocks.investing.domain;

import dev.canverse.stocks.ledger.domain.FinancialAmount;

public record PositionState(FinancialAmount quantity, FinancialAmount remainingBasis, FinancialAmount realizedEconomicPnl) {

    public static PositionState empty() {
        return new PositionState(FinancialAmount.zero(), FinancialAmount.zero(), FinancialAmount.zero());
    }
}
