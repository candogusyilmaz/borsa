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
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @PositiveOrZero
    @Column(nullable = false, precision = 38, scale = 18)
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
        this.quantity = BigDecimal.ZERO;
        this.total = BigDecimal.ZERO;
    }

    public BigDecimal getAveragePrice() {
        if (BigDecimal.ZERO.equals(this.quantity)) return BigDecimal.ZERO;

        return Calculator.divide(this.total, this.quantity);
    }

    public Transaction buy(BigDecimal quantity, BigDecimal price, BigDecimal commission, Instant actionDate) {

        this.quantity = this.quantity.add(quantity);
        this.total = this.total.add(price.multiply(quantity));

        var transaction = Transaction.buy(this, quantity, price, this.quantity, this.total, actionDate);
        this.transactions.add(transaction);
        this.history.add(new PositionHistory(this, PositionHistory.ActionType.BUY));

        return transaction;
    }

    public Transaction sell(BigDecimal quantity, BigDecimal price, BigDecimal commission, Instant actionDate) {
        if (quantity.compareTo(this.quantity) > 0) {
            throw new IllegalArgumentException("Not enough quantity");
        }

        if (this.quantity.equals(quantity)) {
            this.total = BigDecimal.ZERO;
            this.quantity = BigDecimal.ZERO;
        } else {
            this.total = this.total.subtract(Calculator.divide(this.total.multiply(quantity), this.quantity));
            this.quantity = this.quantity.subtract(quantity);
        }

        var transaction = Transaction.sell(this, quantity, price, this.quantity, this.total, actionDate);
        this.transactions.add(transaction);
        this.history.add(new PositionHistory(this, PositionHistory.ActionType.SELL));

        return transaction;
    }

    public void undo() {
        this.history.removeFirst();

        if (this.history.isEmpty()) {
            this.quantity = BigDecimal.ZERO;
            this.total = BigDecimal.ZERO;
        } else {
            this.quantity = history.getFirst().getQuantity();
            this.total = history.getFirst().getTotal();
        }

        this.transactions.removeFirst();
    }
}
