package dev.canverse.stocks.service.member.model;

import dev.canverse.stocks.domain.Calculator;

import java.math.BigDecimal;
import java.util.List;

public record Balance(List<Stock> stocks) {
    public BigDecimal getTotalProfit() {
        return stocks.stream().map(Stock::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCost() {
        return stocks.stream().map(Stock::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalProfitPercentage() {
        if (getTotalProfit().equals(BigDecimal.ZERO))
            return BigDecimal.ZERO;

        return Calculator.calculatePercentage(getTotalProfit(), getTotalCost());
    }

    public BigDecimal getTotalValue() {
        return stocks.stream().map(Stock::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Stock(String id, String symbol, BigDecimal dailyChange, BigDecimal dailyChangePercent, int quantity,
                        BigDecimal total, BigDecimal currentPrice) {
        public BigDecimal getPreviousClose() {
            return currentPrice.subtract(dailyChange);
        }

        public BigDecimal getAveragePrice() {
            return Calculator.divide(total, BigDecimal.valueOf(quantity));
        }

        public BigDecimal getProfit() {
            return currentPrice.subtract(getAveragePrice()).multiply(BigDecimal.valueOf(quantity));
        }

        public BigDecimal getProfitPercentage() {
            var diff = currentPrice.subtract(getAveragePrice());

            return Calculator.calculatePercentage(getAveragePrice(), diff);
        }

        public BigDecimal getDailyProfit() {
            return currentPrice.subtract(getAveragePrice()).multiply(BigDecimal.valueOf(quantity));
        }

        public BigDecimal getValue() {
            return currentPrice.multiply(BigDecimal.valueOf(quantity));
        }

        public BigDecimal getCost() {
            return getAveragePrice().multiply(BigDecimal.valueOf(quantity));
        }
    }
}
