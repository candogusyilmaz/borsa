package dev.canverse.stocks.investing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.canverse.stocks.investing.domain.CalculationPolicy;
import dev.canverse.stocks.investing.domain.PositionProjection;
import dev.canverse.stocks.investing.domain.TradeEvent;
import dev.canverse.stocks.investing.domain.TradeSettlement;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.domain.WeightedAverageEconomicV1;
import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.ProjectionStatus;
import dev.canverse.stocks.platform.error.AppException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeightedAverageEconomicV1Test {

    private static final Instant T1 = Instant.parse("2026-09-18T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-18T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-09-18T11:00:00Z");

    @Test
    void settlesGrossOnceWithCurrencyMinorUnitHalfEvenAndChecksCashEquations() {
        assertThat(buy("1", "1.005", "0", 2).grossAmount().canonical()).isEqualTo("1");
        assertThat(buy("1", "1.015", "0", 2).grossAmount().canonical()).isEqualTo("1.02");
        assertThat(buy("1", "1.025", "0", 2).grossAmount().canonical()).isEqualTo("1.02");
        assertThat(buy("1", "1.035", "0", 2).grossAmount().canonical()).isEqualTo("1.04");

        var buy = buy("2", "10", "0.50", 2);
        assertThat(buy.grossAmount().canonical()).isEqualTo("20");
        assertThat(buy.cashDelta().canonical()).isEqualTo("-20.5");
        assertThat(buy.quantityDelta().canonical()).isEqualTo("2");

        var sell = sell("2", "12", "0.50", 2);
        assertThat(sell.grossAmount().canonical()).isEqualTo("24");
        assertThat(sell.cashDelta().canonical()).isEqualTo("23.5");
        assertThat(sell.quantityDelta().canonical()).isEqualTo("-2");
    }

    @Test
    void rejectsCommissionBeyondMinorUnitAndNonPositiveSettledAmounts() {
        assertThatThrownBy(() -> sell("1", "10", "0.001", 2)).isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode()).isEqualTo(InvestingErrorCode.INVALID_SETTLED_PRECISION);
        assertThatThrownBy(() -> buy("0.001", "0.01", "0", 2)).isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode()).isEqualTo(InvestingErrorCode.INVALID_SETTLED_PRECISION);
        assertThatThrownBy(() -> TradeSettlement.calculate(TradeSide.BUY, amount("99999999999999999999"), amount("99999999999999999999"), amount("0"), 2))
                .isInstanceOf(AppException.class).extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(InvestingErrorCode.INVALID_SETTLED_PRECISION);
        assertThatThrownBy(() -> TradeSettlement.calculate(TradeSide.BUY, amount("1"), amount("99999999999999999999.99"), amount("99999999999999999999.99"), 2))
                .isInstanceOf(AppException.class).extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(InvestingErrorCode.INVALID_SETTLED_PRECISION);
        assertThatThrownBy(() -> sell("1", "1", "1", 2)).isInstanceOf(AppException.class).extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(InvestingErrorCode.TRADE_PROCEEDS_NOT_POSITIVE);
        assertThatThrownBy(() -> TradeSettlement.calculate(TradeSide.BUY, amount("0"), amount("1"), amount("0"), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeSettlement.calculate(TradeSide.BUY, amount("-1"), amount("1"), amount("0"), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeSettlement.calculate(TradeSide.BUY, amount("1"), amount("-1"), amount("0"), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeSettlement.calculate(TradeSide.BUY, amount("1"), amount("1"), amount("-0.01"), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pinsScaleEighteenPartialAllocationRemainderAndFullClose() {
        var buy = event("10000000-0000-4000-8000-000000000001", TradeSide.BUY, "3", "1", "0", T1, 0);
        var firstSale = event("10000000-0000-4000-8000-000000000002", TradeSide.SELL, "-1", "1", "0", T2, 0);
        var secondSale = event("10000000-0000-4000-8000-000000000003", TradeSide.SELL, "-1", "1", "0", T3, 0);
        var first = WeightedAverageEconomicV1.replay(List.of(buy, firstSale));
        assertThat(first.disposals().get(firstSale.activityId()).allocatedBasis().canonical()).isEqualTo("0.333333333333333333");
        assertThat(first.state().remainingBasis().canonical()).isEqualTo("0.666666666666666667");

        var second = WeightedAverageEconomicV1.replay(List.of(buy, firstSale, secondSale));
        assertThat(second.disposals().get(secondSale.activityId()).allocatedBasis().canonical()).isEqualTo("0.333333333333333334");
        assertThat(second.state().remainingBasis().canonical()).isEqualTo("0.333333333333333333");

        var fullClose = event("10000000-0000-4000-8000-000000000004", TradeSide.SELL, "-1", "1", "0", T3.plusSeconds(1), 0);
        var closed = WeightedAverageEconomicV1.replay(List.of(buy, firstSale, secondSale, fullClose));
        assertThat(closed.disposals().get(fullClose.activityId()).allocatedBasis().canonical()).isEqualTo("0.333333333333333333");
        assertThat(closed.state().quantity().canonical()).isEqualTo("0");
        assertThat(closed.state().remainingBasis().canonical()).isEqualTo("0");
    }

    @Test
    void capitalizesBuyFeesRealizesSellFeesAndStartsFreshBasisAfterClose() {
        var buyOne = event("20000000-0000-4000-8000-000000000001", TradeSide.BUY, "10", "100", "2", T1, 1);
        var buyTwo = event("20000000-0000-4000-8000-000000000002", TradeSide.BUY, "2", "40", "1", T2, 1);
        var partialSale = event("20000000-0000-4000-8000-000000000003", TradeSide.SELL, "-3", "180", "5", T3, 1);
        var partial = WeightedAverageEconomicV1.replay(List.of(buyOne, buyTwo, partialSale));
        assertThat(partial.state().quantity().canonical()).isEqualTo("9");
        assertThat(partial.state().remainingBasis().canonical()).isEqualTo("107.25");
        assertThat(partial.disposals().get(partialSale.activityId()).allocatedBasis().canonical()).isEqualTo("35.75");
        assertThat(partial.disposals().get(partialSale.activityId()).realizedEconomicPnl().canonical()).isEqualTo("139.25");

        var fullClose = event("20000000-0000-4000-8000-000000000004", TradeSide.SELL, "-9", "90", "3", T3.plusSeconds(1), 1);
        var reopen = event("20000000-0000-4000-8000-000000000005", TradeSide.BUY, "4", "20", "1", T3.plusSeconds(2), 1);
        var reopened = WeightedAverageEconomicV1.replay(List.of(buyOne, buyTwo, partialSale, fullClose, reopen));
        assertThat(reopened.disposals().get(fullClose.activityId()).allocatedBasis().canonical()).isEqualTo("107.25");
        assertThat(reopened.state().quantity().canonical()).isEqualTo("4");
        assertThat(reopened.state().remainingBasis().canonical()).isEqualTo("21");
        assertThat(reopened.state().realizedEconomicPnl().canonical()).isEqualTo("119");
    }

    @Test
    void aLossReducesRealizedEconomicPnlWithoutChangingClosedBasis() {
        var buy = event("25000000-0000-4000-8000-000000000001", TradeSide.BUY, "1", "100", "0", T1, 0);
        var lossSale = event("25000000-0000-4000-8000-000000000002", TradeSide.SELL, "-1", "50", "1", T2, 0);
        var replay = WeightedAverageEconomicV1.replay(List.of(buy, lossSale));

        assertThat(replay.disposals().get(lossSale.activityId()).allocatedBasis().canonical()).isEqualTo("100");
        assertThat(replay.disposals().get(lossSale.activityId()).realizedEconomicPnl().canonical()).isEqualTo("-51");
        assertThat(replay.state().quantity().canonical()).isEqualTo("0");
        assertThat(replay.state().remainingBasis().canonical()).isEqualTo("0");
        assertThat(replay.state().realizedEconomicPnl().canonical()).isEqualTo("-51");
    }

    @Test
    void replayMapsPositionArithmeticBeyondDatabasePrecisionToAStableProblem() {
        var maximumBasisBuy = event("26000000-0000-4000-8000-000000000001", TradeSide.BUY, "1", "99999999999999999999", "0", T1, 0);
        var overflowingBasisBuy = event("26000000-0000-4000-8000-000000000002", TradeSide.BUY, "1", "1", "0", T2, 0);

        assertThatThrownBy(() -> WeightedAverageEconomicV1.replay(List.of(maximumBasisBuy, overflowingBasisBuy))).isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode()).isEqualTo(InvestingErrorCode.INVALID_SETTLED_PRECISION);
    }

    @Test
    void backdatedFactsReplayByEffectiveTimeAndExplicitSequenceRegardlessOfInputOrder() {
        var earlyBuy = event("30000000-0000-4000-8000-000000000001", TradeSide.BUY, "2", "20", "0", T1, 5);
        var sameTimeBuy = event("30000000-0000-4000-8000-000000000002", TradeSide.BUY, "3", "60", "0", T2, 2);
        var sameTimeSale = event("30000000-0000-4000-8000-000000000003", TradeSide.SELL, "-1", "30", "0", T2, 3);
        var backdatedBuy = event("30000000-0000-4000-8000-000000000004", TradeSide.BUY, "1", "5", "0", T1.plusSeconds(1), 0);
        var reverseInsertion = WeightedAverageEconomicV1.replay(List.of(sameTimeSale, sameTimeBuy, backdatedBuy, earlyBuy));
        var economicInsertion = WeightedAverageEconomicV1.replay(List.of(earlyBuy, backdatedBuy, sameTimeBuy, sameTimeSale));

        assertThat(reverseInsertion.state()).isEqualTo(economicInsertion.state());
        assertThat(reverseInsertion.state().quantity().canonical()).isEqualTo("5");
        assertThat(reverseInsertion.state().remainingBasis().canonical()).isEqualTo("70.833333333333333333");
        assertThat(reverseInsertion.asOf()).isEqualTo(T2);
        assertThat(reverseInsertion.watermarkActivityId()).isEqualTo(sameTimeSale.activityId());
    }

    @Test
    void reversalMatchesHistoryWithoutOriginalAndRejectsDuplicateOrderOrShortHistory() {
        var buy = event("40000000-0000-4000-8000-000000000001", TradeSide.BUY, "4", "40", "1", T1, 1);
        var sale = event("40000000-0000-4000-8000-000000000002", TradeSide.SELL, "-1", "20", "1", T2, 1);
        var reversal = TradeEvent.reversal(uuid("40000000-0000-4000-8000-000000000003"), sale.activityId(), T2, 1);
        assertThat(WeightedAverageEconomicV1.replay(List.of(buy, sale, reversal)).state()).isEqualTo(WeightedAverageEconomicV1.replay(List.of(buy)).state());

        var duplicate = event("40000000-0000-4000-8000-000000000004", TradeSide.BUY, "1", "1", "0", T1, 1);
        assertThatThrownBy(() -> WeightedAverageEconomicV1.replay(List.of(buy, duplicate))).isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode()).isEqualTo(InvestingErrorCode.DUPLICATE_ECONOMIC_ORDER);

        var oversell = event("40000000-0000-4000-8000-000000000005", TradeSide.SELL, "-5", "25", "0", T2, 2);
        assertThatThrownBy(() -> WeightedAverageEconomicV1.replay(List.of(buy, oversell))).isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode()).isEqualTo(InvestingErrorCode.INSUFFICIENT_POSITION_QUANTITY);
        assertThatThrownBy(() -> WeightedAverageEconomicV1
                .replay(List.of(buy, oversell, TradeEvent.reversal(uuid("40000000-0000-4000-8000-000000000006"), buy.activityId(), T1, 1))))
                .isInstanceOf(AppException.class).extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(InvestingErrorCode.INSUFFICIENT_POSITION_QUANTITY);
    }

    @Test
    void positionProjectionCarriesCurrentPolicyAndBuildWatermark() {
        var trade = event("50000000-0000-4000-8000-000000000001", TradeSide.BUY, "1.5", "10", "1", T1, 7);
        var replay = WeightedAverageEconomicV1.replay(List.of(trade));
        var projection = PositionProjection.create(uuid("50000000-0000-4000-8000-000000000002"), uuid("50000000-0000-4000-8000-000000000003"),
                uuid("50000000-0000-4000-8000-000000000004"), uuid("50000000-0000-4000-8000-000000000005"), "USD", replay, T3);

        assertThat(projection.getCalculationPolicy()).isEqualTo(CalculationPolicy.WEIGHTED_AVERAGE_ECONOMIC_V1);
        assertThat(projection.getProjectionStatus()).isEqualTo(ProjectionStatus.CURRENT);
        assertThat(projection.getAsOf()).isEqualTo(T1);
        assertThat(projection.getInputWatermarkActivityId()).isEqualTo(trade.activityId());
        assertThat(projection.getLastSuccessfulBuildAt()).isEqualTo(T3);
        assertThat(projection.getStaleFrom()).isNull();
        assertThat(projection.quantity().canonical()).isEqualTo("1.5");
        assertThat(projection.remainingBasis().canonical()).isEqualTo("11");

        assertThatThrownBy(() -> projection.beginRebuild(T3.plusSeconds(1))).isInstanceOf(IllegalStateException.class);

        var laterTrade = event("50000000-0000-4000-8000-000000000006", TradeSide.BUY, "2", "20", "0", T2, 1);
        var rebuilt = WeightedAverageEconomicV1.replay(List.of(trade, laterTrade));
        projection.markStale(T2, T3.plusSeconds(1));

        assertThat(projection.getProjectionStatus()).isEqualTo(ProjectionStatus.STALE);
        assertThat(projection.getStaleFrom()).isEqualTo(T2);
        assertThat(projection.getAsOf()).isEqualTo(T1);
        assertThat(projection.getInputWatermarkActivityId()).isEqualTo(trade.activityId());
        assertThat(projection.getLastSuccessfulBuildAt()).isEqualTo(T3);
        assertThat(projection.getUpdatedAt()).isEqualTo(T3.plusSeconds(1));

        projection.beginRebuild(T3.plusSeconds(2));
        assertThat(projection.getProjectionStatus()).isEqualTo(ProjectionStatus.REBUILDING);
        assertThat(projection.getStaleFrom()).isEqualTo(T2);
        assertThat(projection.getLastSuccessfulBuildAt()).isEqualTo(T3);
        assertThat(projection.getUpdatedAt()).isEqualTo(T3.plusSeconds(2));

        projection.completeRebuild(rebuilt, T3.plusSeconds(3));
        assertThat(projection.getProjectionStatus()).isEqualTo(ProjectionStatus.CURRENT);
        assertThat(projection.getAsOf()).isEqualTo(T2);
        assertThat(projection.getInputWatermarkActivityId()).isEqualTo(laterTrade.activityId());
        assertThat(projection.getLastSuccessfulBuildAt()).isEqualTo(T3.plusSeconds(3));
        assertThat(projection.getStaleFrom()).isNull();
        assertThat(projection.getUpdatedAt()).isEqualTo(T3.plusSeconds(3));
        assertThat(projection.quantity().canonical()).isEqualTo("3.5");
        assertThat(projection.remainingBasis().canonical()).isEqualTo("31");

        projection.markStale(T1.minusSeconds(1), T3.plusSeconds(4));
        projection.beginRebuild(T3.plusSeconds(5));
        projection.failRebuild(T3.plusSeconds(6));

        assertThat(projection.getProjectionStatus()).isEqualTo(ProjectionStatus.FAILED);
        assertThat(projection.getStaleFrom()).isEqualTo(T1.minusSeconds(1));
        assertThat(projection.getAsOf()).isEqualTo(T2);
        assertThat(projection.getInputWatermarkActivityId()).isEqualTo(laterTrade.activityId());
        assertThat(projection.getLastSuccessfulBuildAt()).isEqualTo(T3.plusSeconds(3));
        assertThat(projection.getUpdatedAt()).isEqualTo(T3.plusSeconds(6));
        assertThat(projection.quantity().canonical()).isEqualTo("3.5");
        assertThat(projection.remainingBasis().canonical()).isEqualTo("31");
    }

    private static TradeSettlement buy(String quantity, String unitPrice, String commission, int minorUnit) {
        return TradeSettlement.calculate(TradeSide.BUY, amount(quantity), amount(unitPrice), amount(commission), minorUnit);
    }

    private static TradeSettlement sell(String quantity, String unitPrice, String commission, int minorUnit) {
        return TradeSettlement.calculate(TradeSide.SELL, amount(quantity), amount(unitPrice), amount(commission), minorUnit);
    }

    private static TradeEvent event(String id, TradeSide side, String quantityDelta, String gross, String commission, Instant effectiveAt, long sequence) {
        return new TradeEvent(uuid(id), side, null, amount(quantityDelta), amount(gross), amount(commission), effectiveAt, sequence);
    }

    private static FinancialAmount amount(String value) {
        return FinancialAmount.parse(value);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
