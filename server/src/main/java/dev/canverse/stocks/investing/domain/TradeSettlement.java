package dev.canverse.stocks.investing.domain;

import dev.canverse.stocks.investing.error.InvestingErrorCode;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.platform.error.AppException;
import java.math.RoundingMode;
import java.util.Objects;

/** Settles manually calculated gross once at the trade currency's minor-unit boundary. */
public record TradeSettlement(
        TradeSide side,
        FinancialAmount quantity,
        FinancialAmount unitPrice,
        FinancialAmount commissionAmount,
        FinancialAmount grossAmount,
        FinancialAmount cashDelta
) {

    public static TradeSettlement calculate(TradeSide side, FinancialAmount quantity, FinancialAmount unitPrice, FinancialAmount commissionAmount,
            int minorUnit) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(commissionAmount, "commissionAmount");
        if (!quantity.isPositive() || !unitPrice.isPositive() || commissionAmount.isNegative() || minorUnit < 0 || minorUnit > FinancialAmount.MAX_SCALE) {
            throw new IllegalArgumentException("Trade amounts must be positive, commission non-negative, and currency scale supported");
        }
        if (commissionAmount.value().stripTrailingZeros().scale() > minorUnit) {
            throw new AppException(InvestingErrorCode.INVALID_SETTLED_PRECISION);
        }

        FinancialAmount grossAmount;
        try {
            grossAmount = FinancialAmount.of(quantity.value().multiply(unitPrice.value()).setScale(minorUnit, RoundingMode.HALF_EVEN));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw new AppException(InvestingErrorCode.INVALID_SETTLED_PRECISION, exception);
        }
        if (!grossAmount.isPositive()) {
            throw new AppException(InvestingErrorCode.INVALID_SETTLED_PRECISION);
        }

        FinancialAmount cashDelta;
        try {
            cashDelta = switch (side) {
                case BUY -> grossAmount.add(commissionAmount).negate();
                case SELL -> grossAmount.subtract(commissionAmount);
            };
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw new AppException(InvestingErrorCode.INVALID_SETTLED_PRECISION, exception);
        }
        if (side == TradeSide.SELL && !cashDelta.isPositive()) {
            throw new AppException(InvestingErrorCode.TRADE_PROCEEDS_NOT_POSITIVE);
        }
        return new TradeSettlement(side, quantity, unitPrice, commissionAmount, grossAmount, cashDelta);
    }

    public FinancialAmount quantityDelta() {
        return side == TradeSide.BUY ? quantity : quantity.negate();
    }
}
