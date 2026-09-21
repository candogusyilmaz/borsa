package dev.canverse.stocks.investing.domain;

import dev.canverse.stocks.ledger.domain.FinancialAmount;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable replay input projected from a security posting and its activity. */
public record TradeEvent(
        UUID activityId,
        TradeSide side,
        UUID reversesActivityId,
        FinancialAmount quantityDelta,
        FinancialAmount grossAmount,
        FinancialAmount commissionAmount,
        Instant effectiveAt,
        long economicSequence
) {

    public TradeEvent {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        if (economicSequence < 0) {
            throw new IllegalArgumentException("economicSequence must be non-negative");
        }
        if (reversesActivityId == null) {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(quantityDelta, "quantityDelta");
            Objects.requireNonNull(grossAmount, "grossAmount");
            Objects.requireNonNull(commissionAmount, "commissionAmount");
            if (!grossAmount.isPositive() || commissionAmount.isNegative() || (side == TradeSide.BUY && !quantityDelta.isPositive()) ||
                    (side == TradeSide.SELL && !quantityDelta.isNegative())) {
                throw new IllegalArgumentException("Trade event has an invalid side or amount shape");
            }
        } else if (side != null || quantityDelta != null || grossAmount != null || commissionAmount != null) {
            throw new IllegalArgumentException("Reversal event cannot carry an original trade payload");
        }
    }

    public static TradeEvent trade(UUID activityId, TradeSettlement trade, Instant effectiveAt, long economicSequence) {
        return new TradeEvent(activityId, trade.side(), null, trade.quantityDelta(), trade.grossAmount(), trade.commissionAmount(), effectiveAt,
                economicSequence);
    }

    public static TradeEvent reversal(UUID activityId, UUID reversesActivityId, Instant effectiveAt, long economicSequence) {
        return new TradeEvent(activityId, null, Objects.requireNonNull(reversesActivityId, "reversesActivityId"), null, null, null, effectiveAt,
                economicSequence);
    }

    public boolean isReversal() {
        return reversesActivityId != null;
    }
}
