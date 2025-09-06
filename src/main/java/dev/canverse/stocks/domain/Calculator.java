package dev.canverse.stocks.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Calculator {
    public static final int SCALE = 18;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, SCALE, ROUNDING_MODE);
    }

    public static BigDecimal calculatePercentage(BigDecimal total, BigDecimal part) {
        return part.multiply(BigDecimal.valueOf(100)).divide(total, SCALE, ROUNDING_MODE);
    }

    public static BigDecimal calculatePercentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || BigDecimal.ZERO.compareTo(previous) == 0) {
            return null;
        }

        var difference = current.subtract(previous);

        if (previous.compareTo(BigDecimal.ZERO) < 0 && current.compareTo(BigDecimal.ZERO) < 0) {
            var improvementInLoss = previous.subtract(current); // How much loss was reduced/increased
            return Calculator.divide(improvementInLoss, previous.abs())
                    .multiply(BigDecimal.valueOf(100));
        }

        if (previous.compareTo(BigDecimal.ZERO) < 0 && current.compareTo(BigDecimal.ZERO) >= 0) {
            return Calculator.divide(difference, previous.abs())
                    .multiply(BigDecimal.valueOf(100));
        }

        // Standard calculation for other cases
        return Calculator.divide(difference, previous)
                .multiply(BigDecimal.valueOf(100));
    }
}
