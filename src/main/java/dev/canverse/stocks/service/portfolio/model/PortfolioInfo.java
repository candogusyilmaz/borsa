package dev.canverse.stocks.service.portfolio.model;

import dev.canverse.stocks.domain.Calculator;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioInfo(
        @NotNull
        List<Stock> stocks) {
    @NotNull
    public BigDecimal getTotalProfit() {
        return stocks.stream().map(Stock::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @NotNull
    public BigDecimal getTotalCost() {
        return stocks.stream().map(Stock::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @NotNull
    public BigDecimal getTotalProfitPercentage() {
        if (getTotalProfit().equals(BigDecimal.ZERO))
            return BigDecimal.ZERO;

        return Calculator.calculatePercentage(getTotalCost(), getTotalProfit());
    }

    @NotNull
    public BigDecimal getTotalValue() {
        return stocks.stream().map(Stock::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Stock(
            @NotNull
            String id,
            @NotNull
            String symbol,
            @NotNull
            BigDecimal dailyChange,
            @NotNull
            BigDecimal previousClose,
            @NotNull
            BigDecimal dailyChangePercent,
            @NotNull
            BigDecimal quantity,
            @NotNull
            BigDecimal total,
            @NotNull
            BigDecimal currentPrice,
            @NotNull
            BigDecimal averagePrice,
            @NotNull
            BigDecimal dailyProfit,
            @NotNull
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
            return averagePrice.multiply(quantity);
        }
    }
}