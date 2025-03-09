package dev.canverse.stocks.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Calculator {
    public static final int SCALE = 6;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, SCALE, ROUNDING_MODE);
    }

    public static BigDecimal calculatePercentage(BigDecimal total, BigDecimal part) {
        return part.multiply(BigDecimal.valueOf(100)).divide(total, SCALE, ROUNDING_MODE);
    }
}
