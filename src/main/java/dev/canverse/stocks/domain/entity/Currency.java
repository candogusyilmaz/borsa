package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "currencies")
public class Currency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private int decimals;

    // Exchange rate to USD (USD = 1.0)
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal exchangeRate;

    @Column(nullable = false)
    private Instant exchangeRateUpdatedAt;

    @Column(nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected Currency() {
    }

    public void updateExchangeRate(BigDecimal exchangeRate, Instant updatedAt) {
        this.exchangeRate = exchangeRate;
        this.exchangeRateUpdatedAt = updatedAt;
    }
}
