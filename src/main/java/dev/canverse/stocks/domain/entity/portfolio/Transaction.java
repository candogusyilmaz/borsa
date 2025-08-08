package dev.canverse.stocks.domain.entity.portfolio;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import dev.canverse.stocks.domain.Calculator;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(schema = "portfolio", name = "transactions")
public class Transaction implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Position position;

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
    @Column(name = "tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal commission;

    @NotNull
    @Column(nullable = false)
    private Instant actionDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @JsonManagedReference
    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL)
    @JoinColumn(name = "id", referencedColumnName = "trade_id")
    private TransactionPerformance performance;

    protected Transaction() {
    }

    protected Transaction(Position position, Type type, int quantity, BigDecimal price, BigDecimal commission, Instant actionDate) {
        this.position = position;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.commission = commission;
        this.actionDate = actionDate;

        if (type == Type.SELL) {
            var profit = price.subtract(position.getAveragePrice()).multiply(BigDecimal.valueOf(quantity));
            var returnPercentage = Calculator.divide(price.subtract(position.getAveragePrice()).multiply(BigDecimal.valueOf(100)), position.getAveragePrice());

            this.performance = new TransactionPerformance(this, profit, returnPercentage);
        }
    }

    public enum Type {
        BUY,
        SELL
    }

    public BigDecimal getTotal() {
        return this.price.multiply(BigDecimal.valueOf(this.quantity));
    }
}
