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
    private User user;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Stock stock;

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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Trade() {
    }

    public Trade(User user, Stock stock, Type type, int quantity, BigDecimal price, BigDecimal tax) {
        this.user = user;
        this.stock = stock;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.tax = tax;
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
