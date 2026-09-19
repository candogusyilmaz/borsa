package dev.canverse.stocks.investing.domain;

import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.platform.error.AppException;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Deterministic full replay for WEIGHTED_AVERAGE_ECONOMIC_V1. */
public final class WeightedAverageEconomicV1 {

    private static final Comparator<TradeEvent> ECONOMIC_ORDER = Comparator.comparing(TradeEvent::effectiveAt).thenComparingLong(TradeEvent::economicSequence)
            .thenComparing(TradeEvent::activityId);

    private WeightedAverageEconomicV1() {}

    public static TradeReplayResult replay(List<TradeEvent> input) {
        try {
            return replayExact(input);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw new AppException(InvestingErrorCode.INVALID_SETTLED_PRECISION, exception);
        }
    }

    private static TradeReplayResult replayExact(List<TradeEvent> input) {
        var events = List.copyOf(input);
        var originals = new HashMap<UUID, TradeEvent>();
        var reversed = new HashSet<UUID>();
        var orderKeys = new HashSet<EconomicOrder>();
        for (var event : events) {
            if (event.isReversal()) {
                continue;
            }
            var order = new EconomicOrder(event.effectiveAt(), event.economicSequence());
            if (!orderKeys.add(order)) {
                throw new AppException(InvestingErrorCode.DUPLICATE_ECONOMIC_ORDER);
            }
            if (originals.put(event.activityId(), event) != null) {
                throw new IllegalStateException("Trade activity identity is duplicated in replay input");
            }
        }
        for (var event : events) {
            if (event.isReversal()) {
                if (!originals.containsKey(event.reversesActivityId()) || !reversed.add(event.reversesActivityId())) {
                    throw new IllegalStateException("Trade reversal input does not identify one unreversed original trade");
                }
            }
        }

        var orderedInput = events.stream().sorted(ECONOMIC_ORDER).toList();
        var activeTrades = events.stream().filter(event -> !event.isReversal() && !reversed.contains(event.activityId())).sorted(ECONOMIC_ORDER).toList();
        var quantity = FinancialAmount.zero();
        var remainingBasis = FinancialAmount.zero();
        var realizedPnl = FinancialAmount.zero();
        var disposals = new HashMap<UUID, TradeReplayResult.Disposal>();
        for (var event : activeTrades) {
            if (event.side() == TradeSide.BUY) {
                quantity = exact(quantity.add(event.quantityDelta()));
                remainingBasis = exact(remainingBasis.add(event.grossAmount()).add(event.commissionAmount()));
                continue;
            }

            var disposedQuantity = event.quantityDelta().negate();
            if (disposedQuantity.compareTo(quantity) > 0) {
                throw new AppException(InvestingErrorCode.INSUFFICIENT_POSITION_QUANTITY);
            }
            var allocatedBasis = disposedQuantity.compareTo(quantity) == 0 ? remainingBasis : exact(FinancialAmount
                    .of(remainingBasis.value().multiply(disposedQuantity.value()).divide(quantity.value(), FinancialAmount.MAX_SCALE, RoundingMode.HALF_EVEN)));
            quantity = exact(quantity.subtract(disposedQuantity));
            remainingBasis = exact(remainingBasis.subtract(allocatedBasis));
            if (quantity.isZero()) {
                remainingBasis = FinancialAmount.zero();
            }
            var realized = exact(event.grossAmount().subtract(event.commissionAmount()).subtract(allocatedBasis));
            realizedPnl = exact(realizedPnl.add(realized));
            disposals.put(event.activityId(), new TradeReplayResult.Disposal(allocatedBasis, realized));
        }

        var watermark = orderedInput.isEmpty() ? null : orderedInput.getLast().activityId();
        var asOf = orderedInput.isEmpty() ? null : orderedInput.getLast().effectiveAt();
        return new TradeReplayResult(new PositionState(quantity, remainingBasis, realizedPnl), disposals, asOf, watermark);
    }

    private static FinancialAmount exact(FinancialAmount amount) {
        return FinancialAmount.of(amount.value());
    }

    private record EconomicOrder(Instant effectiveAt, long economicSequence) {}
}
