package dev.canverse.stocks.domain.entity;

import dev.canverse.stocks.domain.Calculator;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "holdings")
public class Holding implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Stock stock;

    @PositiveOrZero
    @Column(nullable = false)
    private int quantity;

    @PositiveOrZero
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal total;

    @PositiveOrZero
    @Column(name = "total_tax", nullable = false, precision = 20, scale = 8)
    private BigDecimal commission;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Trade> trades = new ArrayList<>();

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<HoldingHistory> history = new ArrayList<>();

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<HoldingDailySnapshot> dailySnapshots = new ArrayList<>();

    protected Holding() {
    }

    public Holding(User user, Stock stock) {
        this.user = user;
        this.stock = stock;
        this.quantity = 0;
        this.total = BigDecimal.ZERO;
        this.commission = BigDecimal.ZERO;
    }

    public BigDecimal getAveragePrice() {
        if (this.quantity == 0) return BigDecimal.ZERO;

        return Calculator.divide(this.total, BigDecimal.valueOf(this.quantity));
    }

    public void buy(int quantity, BigDecimal price, BigDecimal commission, Instant actionDate) {
        this.trades.add(new Trade(this, Trade.Type.BUY, quantity, price, commission, actionDate));

        this.quantity += quantity;
        this.total = this.total.add(price.multiply(BigDecimal.valueOf(quantity)));

        this.history.add(new HoldingHistory(this, HoldingHistory.ActionType.BUY));
    }

    public void sell(int quantity, BigDecimal price, BigDecimal commission, Instant actionDate) {
        if (this.quantity < quantity) {
            throw new IllegalArgumentException("Not enough quantity");
        }

        this.trades.add(new Trade(this, Trade.Type.SELL, quantity, price, commission, actionDate));

        this.total = this.total.subtract(Calculator.divide(this.total.multiply(BigDecimal.valueOf(quantity)), BigDecimal.valueOf(this.quantity)));
        this.quantity -= quantity;

        this.history.add(new HoldingHistory(this, HoldingHistory.ActionType.SELL));
    }

    public void undo() {
        var latestTrade = this.trades.stream().findFirst().orElseThrow(() -> new IllegalStateException("No trades found"));

        if (latestTrade.getType() == Trade.Type.BUY) {
            undoBuy(latestTrade);
        } else {
            undoSell(latestTrade);
        }

        this.trades.remove(latestTrade);
        this.history.add(new HoldingHistory(this, HoldingHistory.ActionType.UNDO));
    }

    private void undoSell(Trade latestTrade) {
        this.quantity += latestTrade.getQuantity();
        this.total = this.total.add(latestTrade.getTotal());

        var averageCommissionPerUnit = Calculator.divide(this.commission, BigDecimal.valueOf(this.quantity));
        this.commission = this.commission.add(averageCommissionPerUnit.multiply(BigDecimal.valueOf(latestTrade.getQuantity())));
    }

    private void undoBuy(Trade latestTrade) {
        this.quantity = this.quantity - latestTrade.getQuantity();

        if (this.quantity == 0) {
            this.total = BigDecimal.ZERO;
            this.commission = BigDecimal.ZERO;
        } else {
            this.total = this.total.subtract(latestTrade.getTotal());
            this.commission = this.commission.subtract(latestTrade.getCommission());
        }
    }

    public void adjustStockSplit(BigDecimal ratio) {
        this.quantity = BigDecimal.valueOf(quantity)
                .multiply(ratio)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        this.history.add(new HoldingHistory(this, HoldingHistory.ActionType.STOCK_SPLIT));
    }
}
