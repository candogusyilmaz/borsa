package dev.canverse.stocks.service.portfolio.model;

import dev.canverse.stocks.domain.Calculator;

import java.math.BigDecimal;

public record RealizedGains(BigDecimal currentPeriod, BigDecimal previousPeriod) {

    private BigDecimal getPercentageChange(BigDecimal current, BigDecimal previous) {
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
