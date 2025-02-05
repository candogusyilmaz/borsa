package dev.canverse.stocks.service.member.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record Balance(List<Stock> stocks) {
    public BigDecimal getTotalProfit() {
        return stocks.stream().map(Stock::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCost() {
        return stocks.stream().map(Stock::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalProfitPercentage() {
        return getTotalProfit().divide(getTotalCost(), RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalValue() {
        return stocks.stream().map(Stock::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Stock(String symbol, int quantity, BigDecimal averagePrice, BigDecimal currentPrice) {
        public BigDecimal getProfit() {
            return currentPrice.subtract(averagePrice).multiply(BigDecimal.valueOf(quantity));
        }

        public BigDecimal getProfitPercentage() {
            return currentPrice.subtract(averagePrice).divide(averagePrice, RoundingMode.HALF_UP);
        }

        public BigDecimal getValue() {
            return currentPrice.multiply(BigDecimal.valueOf(quantity));
        }

        public BigDecimal getCost() {
            return averagePrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
