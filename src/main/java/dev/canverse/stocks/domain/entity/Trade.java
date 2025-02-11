package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Getter
@Entity
@Table(name = "trades")
public class Trade implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Holding holding;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Positive
    @Column(nullable = false)
    private int quantity;

    @Positive
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal tax;

    @NotNull
    @Column(nullable = false)
    private Instant actionDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "trade", cascade = CascadeType.ALL)
    @JoinColumn(name = "id", referencedColumnName = "trade_id")
    private TradePerformance performance;

    protected Trade() {
    }

    protected Trade(Holding holding, Type type, int quantity, BigDecimal price, BigDecimal tax, Instant actionDate) {
        this.holding = holding;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.tax = tax;
        this.actionDate = actionDate;

        if (type == Type.SELL) {
            performance = new TradePerformance(this,
                    price.subtract(holding.getAveragePrice()).multiply(BigDecimal.valueOf(quantity)),
                    price.subtract(holding.getAveragePrice()).divide(holding.getAveragePrice(), RoundingMode.HALF_UP));
        }
    }

    public enum Type {
        BUY,
        SELL
    }

    public BigDecimal getTotalWithoutTax() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getTotal() {
        return getTotalWithoutTax().add(tax);
    }
}
