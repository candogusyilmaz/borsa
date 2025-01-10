package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

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

    @Setter
    @PositiveOrZero
    @Column(nullable = false)
    private int quantity;

    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal averagePrice;

    @Setter
    @PositiveOrZero
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTax;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

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

    public void setAveragePrice(int quantity, BigDecimal price) {
        this.averagePrice = averagePrice.equals(BigDecimal.ZERO) ? price : averagePrice.multiply(BigDecimal.valueOf(this.quantity))
                .add(price.multiply(BigDecimal.valueOf(quantity)))
                .divide(BigDecimal.valueOf(this.quantity + quantity), RoundingMode.HALF_UP);
    }
}
