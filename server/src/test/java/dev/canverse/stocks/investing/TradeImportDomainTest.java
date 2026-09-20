package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.investing.domain.TradeEvent;
import dev.canverse.stocks.investing.domain.TradeSettlement;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.domain.WeightedAverageEconomicV1;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradeImportDomainTest {

    private static final Instant BUY_AT = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant SELL_AT = Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void usesManualSettlementAndWeightedAverageReplayForFundedTrades() {
        var buy = TradeSettlement.calculate(TradeSide.BUY, amount("10"), amount("10"), amount("2"), 2);
        var partialSell = TradeSettlement.calculate(TradeSide.SELL, amount("4"), amount("12"), amount("1"), 2);
        var fullSell = TradeSettlement.calculate(TradeSide.SELL, amount("6"), amount("12"), amount("1"), 2);
        var buyEvent = TradeEvent.trade(id(1), buy, BUY_AT, 1);
        var partialEvent = TradeEvent.trade(id(2), partialSell, SELL_AT, 2);
        var fullEvent = TradeEvent.trade(id(3), fullSell, SELL_AT.plusSeconds(1), 3);

        var partial = WeightedAverageEconomicV1.replay(List.of(buyEvent, partialEvent));
        assertThat(buy.grossAmount().canonical()).isEqualTo("100");
        assertThat(buy.cashDelta().canonical()).isEqualTo("-102");
        assertThat(partialSell.cashDelta().canonical()).isEqualTo("47");
        assertThat(partial.state().quantity().canonical()).isEqualTo("6");
        assertThat(partial.state().remainingBasis().canonical()).isEqualTo("61.2");
        assertThat(partial.disposals().get(id(2)).realizedEconomicPnl().canonical()).isEqualTo("6.2");

        var closed = WeightedAverageEconomicV1.replay(List.of(buyEvent, partialEvent, fullEvent));
        assertThat(closed.state().quantity().canonical()).isEqualTo("0");
        assertThat(closed.state().remainingBasis().canonical()).isEqualTo("0");
        assertThat(closed.state().realizedEconomicPnl().canonical()).isEqualTo("16");

        var reopen = TradeEvent.trade(id(4), TradeSettlement.calculate(TradeSide.BUY, amount("2"), amount("5"), amount("0"), 2), SELL_AT.plusSeconds(2), 4);
        var reopened = WeightedAverageEconomicV1.replay(List.of(buyEvent, partialEvent, fullEvent, reopen));
        assertThat(reopened.state().quantity().canonical()).isEqualTo("2");
        assertThat(reopened.state().remainingBasis().canonical()).isEqualTo("10");
        assertThat(reopened.state().realizedEconomicPnl().canonical()).isEqualTo("16");
    }

    @Test
    void appliesHalfEvenSettlementAndCurrencyPrecisionRules() {
        assertThat(TradeSettlement.calculate(TradeSide.BUY, amount("1"), amount("1.005"), amount("0"), 2).grossAmount().canonical()).isEqualTo("1");
        assertThat(TradeSettlement.calculate(TradeSide.BUY, amount("1"), amount("1.015"), amount("0"), 2).grossAmount().canonical()).isEqualTo("1.02");

        assertThatThrownBy(() -> TradeSettlement.calculate(TradeSide.BUY, amount("1"), amount("1"), amount("0.001"), 2)).isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode()).isEqualTo(InvestingErrorCode.INVALID_SETTLED_PRECISION));
        assertThatThrownBy(() -> TradeSettlement.calculate(TradeSide.SELL, amount("1"), amount("1"), amount("1"), 2)).isInstanceOf(AppException.class)
                .satisfies(exception -> assertThat(((AppException) exception).getErrorCode()).isEqualTo(InvestingErrorCode.TRADE_PROCEEDS_NOT_POSITIVE));
    }

    private static FinancialAmount amount(String value) {
        return FinancialAmount.parse(value);
    }

    private static UUID id(int suffix) {
        return UUID.fromString("10000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
