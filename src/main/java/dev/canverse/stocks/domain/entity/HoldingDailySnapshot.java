package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "holding_daily_snapshots")
public class HoldingDailySnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Holding holding;

    @PositiveOrZero
    @Column(nullable = false)
    private int quantity;

    @PositiveOrZero
    @Column(nullable = true, precision = 15, scale = 2)
    private BigDecimal totalCommission;

    // Recorded average cost price per unit at the snapshot time.
    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal averagePrice;

    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal marketPrice;

    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal previousMarketPrice;

    // Portfolio weight percentage (to be calculated externally)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal portfolioWeightPercentage;

    // Market value = quantity * marketPrice
    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2,
            columnDefinition = "DECIMAL(15,2) GENERATED ALWAYS AS (quantity * market_price) STORED")
    private BigDecimal marketValue;

    // Total cost = quantity * averagePrice
    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2,
            columnDefinition = "DECIMAL(15,2) GENERATED ALWAYS AS (quantity * average_price) STORED")
    private BigDecimal totalCost;

    // Total profit = marketValue - totalCost
    @Column(nullable = false, precision = 15, scale = 2,
            columnDefinition = "DECIMAL(15,2) GENERATED ALWAYS AS ((quantity * market_price) - (quantity * average_price)) STORED")
    private BigDecimal totalProfit;

    // Total profit percentage = (totalProfit / totalCost) * 100
    @Column(nullable = false, precision = 5, scale = 2,
            columnDefinition = "DECIMAL(5,2) GENERATED ALWAYS AS (CASE WHEN (quantity * average_price) > 0 THEN (((quantity * market_price) - (quantity * average_price)) / (quantity * average_price)) * 100 ELSE NULL END) STORED")
    private BigDecimal totalProfitPercentage;

    // Daily profit = marketValue - previousDay's marketValue
    @Column(nullable = false, precision = 15, scale = 2,
            columnDefinition = "DECIMAL(15,2) GENERATED ALWAYS AS ((quantity * market_price) - (quantity * previous_market_price)) STORED")
    private BigDecimal dailyProfit;

    // Daily profit percentage = (dailyProfit / previous day's marketValue) * 100 (computed externally)
    @Column(nullable = false, precision = 5, scale = 2,
            columnDefinition = "DECIMAL(5,2) GENERATED ALWAYS AS (CASE WHEN previous_market_price > 0 THEN ((quantity * market_price) - (quantity * previous_market_price)) / (quantity * previous_market_price) * 100 ELSE NULL END) STORED")
    private BigDecimal dailyProfitPercentage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected HoldingDailySnapshot() {
    }

    public HoldingDailySnapshot(Holding holding, int quantity, BigDecimal averagePrice, BigDecimal marketPrice, BigDecimal previousMarketPrice, BigDecimal portfolioWeightPercentage) {
        this.holding = holding;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.marketPrice = marketPrice;
        this.previousMarketPrice = previousMarketPrice;
        this.portfolioWeightPercentage = portfolioWeightPercentage;
    }
}
