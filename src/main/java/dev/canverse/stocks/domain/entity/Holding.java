package dev.canverse.stocks.domain.entity;

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
import java.util.HashSet;
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
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal averagePrice;

    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTax;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Trade> trades = new HashSet<>();

    @OneToMany(mappedBy = "holding", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<HoldingHistory> history = new HashSet<>();

    protected Holding() {
    }

    public Holding(User user, Stock stock, int quantity, BigDecimal averagePrice, BigDecimal totalTax) {
        this.user = user;
        this.stock = stock;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.totalTax = totalTax;
    }

    public BigDecimal getTotal() {
        return getTotalWithoutTax().add(totalTax);
    }

    public BigDecimal getTotalWithoutTax() {
        return averagePrice.multiply(BigDecimal.valueOf(quantity));
    }

    public void buy(int quantity, BigDecimal price, BigDecimal tax, Instant actionDate) {
        calculateAveragePrice(price, quantity);
        this.totalTax = this.totalTax == null ? tax : this.totalTax.add(tax);

        trades.add(new Trade(this, Trade.Type.BUY, quantity, price, tax, actionDate));
        history.add(new HoldingHistory(this));
    }

    public void sell(int quantity, BigDecimal price, BigDecimal tax, Instant actionDate) {
        if (this.quantity < quantity) {
            throw new IllegalArgumentException("Not enough quantity");
        }

        this.quantity -= quantity;
        this.totalTax = this.totalTax.add(tax);

        history.add(new HoldingHistory(this));
        trades.add(new Trade(this, Trade.Type.SELL, quantity, price, tax, actionDate));
    }

    private void calculateAveragePrice(BigDecimal price, int quantity) {
        if (this.averagePrice == null || this.averagePrice.equals(BigDecimal.ZERO) || this.quantity == 0) {
            this.quantity += quantity;
            this.averagePrice = price;
            return;
        }

        // ORDER MATTERS!
        this.averagePrice = this.averagePrice.multiply(BigDecimal.valueOf(this.quantity))
                .add(price.multiply(BigDecimal.valueOf(quantity)))
                .divide(BigDecimal.valueOf(this.quantity + quantity), RoundingMode.HALF_UP);
        this.quantity += quantity;
    }
}
