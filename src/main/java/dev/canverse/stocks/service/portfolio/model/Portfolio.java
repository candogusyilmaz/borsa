package dev.canverse.stocks.service.portfolio.model;

import dev.canverse.stocks.domain.Calculator;

import java.math.BigDecimal;
import java.util.List;

public record Portfolio(List<Stock> stocks) {
    public BigDecimal getTotalProfit() {
        return stocks.stream().map(Stock::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCost() {
        return stocks.stream().map(Stock::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCommission() {
        return stocks.stream().map(Stock::commission).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalProfitPercentage() {
        if (getTotalProfit().equals(BigDecimal.ZERO))
            return BigDecimal.ZERO;

        return Calculator.calculatePercentage(getTotalCost(), getTotalProfit());
    }

    public BigDecimal getTotalValue() {
        return stocks.stream().map(Stock::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Stock(
            String id,
            String symbol,
            BigDecimal dailyChange,
            BigDecimal previousClose,
            BigDecimal dailyChangePercent,
            int quantity,
            BigDecimal total,
            BigDecimal commission,
            BigDecimal currentPrice,
            BigDecimal averagePrice,
            BigDecimal dailyProfit,
            BigDecimal value) {

        public BigDecimal getProfit() {
            return value.subtract(getCost());
        }

        public BigDecimal getProfitPercentage() {
            var cost = getCost();

            if (cost.equals(BigDecimal.ZERO))
                return BigDecimal.ZERO;

            return Calculator.calculatePercentage(cost, getProfit());
        }

        public BigDecimal getCost() {
            return averagePrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}