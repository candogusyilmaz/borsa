package dev.canverse.stocks.domain.entity.portfolio;

import dev.canverse.stocks.domain.Calculator;
import dev.canverse.stocks.domain.entity.instrument.Instrument;
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
@Table(schema = "portfolio", name = "positions", indexes = {
        @Index(name = "idx_holdings_portfolio_id", columnList = "portfolio_id")
})
public class Position implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Portfolio portfolio;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Instrument instrument;

    @PositiveOrZero
    @Column(nullable = false)
    private int quantity;

    @PositiveOrZero
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal total;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OrderBy("id DESC")
    @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Transaction> transactions = new ArrayList<>();

    @OrderBy("id DESC")
    @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PositionHistory> history = new ArrayList<>();

    @OrderBy("id DESC")
    @OneToMany(mappedBy = "position", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PositionDailySnapshot> dailySnapshots = new ArrayList<>();

    protected Position() {
    }

    public Position(Portfolio portfolio, Instrument instrument) {
        this.portfolio = portfolio;
        this.instrument = instrument;
        this.quantity = 0;
        this.total = BigDecimal.ZERO;
    }

    public BigDecimal getAveragePrice() {
        if (this.quantity == 0) return BigDecimal.ZERO;

        return Calculator.divide(this.total, BigDecimal.valueOf(this.quantity));
    }

    public void buy(int quantity, BigDecimal price, BigDecimal commission, Instant actionDate) {
        this.transactions.add(new Transaction(this, Transaction.Type.BUY, quantity, price, commission, actionDate));

        this.quantity += quantity;
        this.total = this.total.add(price.multiply(BigDecimal.valueOf(quantity)));

        this.history.add(new PositionHistory(this, PositionHistory.ActionType.BUY));
    }

    public void sell(int quantity, BigDecimal price, BigDecimal commission, Instant actionDate) {
        if (this.quantity < quantity) {
            throw new IllegalArgumentException("Not enough quantity");
        }

        this.transactions.add(new Transaction(this, Transaction.Type.SELL, quantity, price, commission, actionDate));

        // In order to preserve the cost basis when undoing a sell
        // we keep the total as is and adjust it based on the quantity sold
        if (this.quantity == quantity) {
            this.quantity = 0;
        } else {
            this.total = this.total.subtract(Calculator.divide(this.total.multiply(BigDecimal.valueOf(quantity)), BigDecimal.valueOf(this.quantity)));
            this.quantity -= quantity;
        }

        this.history.add(new PositionHistory(this, PositionHistory.ActionType.SELL));
    }

    public void undo() {
        var latestTrade = this.transactions.getFirst();

        if (latestTrade.getType() == Transaction.Type.BUY) {
            undoBuy(latestTrade);
        } else {
            undoSell(latestTrade);
        }

        this.transactions.removeFirst();
    }

    private void undoSell(Transaction latestTransaction) {
        // If quantity is zero this means in last trade we sold all shares
        // Since when we sell all shares we set quantity to zero but total remains
        // Thus we can just add the latest trade's quantity
        if (this.quantity == 0) {
            this.quantity = latestTransaction.getQuantity();
            return;
        }

        int previousQuantity = this.quantity;
        this.quantity += latestTransaction.getQuantity();

        this.total = Calculator.divide(
                this.total.multiply(BigDecimal.valueOf(this.quantity)),
                BigDecimal.valueOf(previousQuantity)
        );
    }

    private void undoBuy(Transaction latestTransaction) {
        this.quantity = this.quantity - latestTransaction.getQuantity();

        if (this.quantity == 0) {
            this.total = BigDecimal.ZERO;
        } else {
            this.total = this.total.subtract(latestTransaction.getTotal());
        }
    }

    public void adjustStockSplit(BigDecimal ratio) {
        this.quantity = BigDecimal.valueOf(quantity).multiply(ratio).setScale(0, RoundingMode.HALF_UP).intValue();

        this.history.add(new PositionHistory(this, PositionHistory.ActionType.STOCK_SPLIT));
    }
}
