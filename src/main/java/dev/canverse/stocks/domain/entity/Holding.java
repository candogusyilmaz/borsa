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
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

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
    @Column(nullable = false, precision = 15, scale = 6)
    private BigDecimal total;

    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTax;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<Trade> trades = new LinkedHashSet<>();

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<HoldingHistory> history = new LinkedHashSet<>();

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<HoldingDailySnapshot> dailySnapshots = new LinkedHashSet<>();

    protected Holding() {
    }

    public Holding(User user, Stock stock) {
        this.user = user;
        this.stock = stock;
        this.quantity = 0;
        this.total = BigDecimal.ZERO;
        this.totalTax = BigDecimal.ZERO;
    }

    public BigDecimal getAveragePrice() {
        if (this.quantity == 0) return BigDecimal.ZERO;

        return Calculator.divide(this.total, BigDecimal.valueOf(this.quantity));
    }

    public void buy(int quantity, BigDecimal price, BigDecimal tax, Instant actionDate) {
        this.quantity += quantity;
        this.total = this.total.add(price.multiply(BigDecimal.valueOf(quantity)));
        this.totalTax = this.totalTax == null ? tax : this.totalTax.add(tax);

        this.trades.add(new Trade(this, Trade.Type.BUY, quantity, price, tax, actionDate));
        this.history.add(new HoldingHistory(this, HoldingHistory.ActionType.BUY));
    }

    public void sell(int quantity, BigDecimal price, BigDecimal tax, Instant actionDate) {
        if (this.quantity < quantity) {
            throw new IllegalArgumentException("Not enough quantity");
        }

        this.quantity -= quantity;
        this.total = this.total.subtract(price.multiply(BigDecimal.valueOf(quantity)));
        this.totalTax = this.totalTax.add(tax);

        this.history.add(new HoldingHistory(this, HoldingHistory.ActionType.SELL));
        this.trades.add(new Trade(this, Trade.Type.SELL, quantity, price, tax, actionDate));
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
        this.totalTax = this.totalTax.add(latestTrade.getTax());
    }

    private void undoBuy(Trade latestTrade) {
        this.quantity = this.quantity - latestTrade.getQuantity();

        if (this.quantity == 0) {
            this.total = BigDecimal.ZERO;
            this.totalTax = BigDecimal.ZERO;
        } else {
            var tradeTotal = latestTrade.getTotal();
            this.total = this.total.subtract(tradeTotal);
            this.totalTax = this.totalTax.subtract(latestTrade.getTax());
        }
    }
}
