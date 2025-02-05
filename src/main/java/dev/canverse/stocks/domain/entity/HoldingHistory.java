package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "holding_history")
public class HoldingHistory implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Holding holding;

    @NotNull
    @Column(nullable = false)
    private int quantity;

    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal averagePrice;

    @NotNull
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTax;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected HoldingHistory() {
    }

    protected HoldingHistory(Holding holding) {
        this.holding = holding;
        this.quantity = holding.getQuantity();
        this.averagePrice = holding.getAveragePrice();
        this.totalTax = holding.getTotalTax();
    }
}
