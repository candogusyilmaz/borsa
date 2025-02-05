package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "stock_snapshots")
public class StockSnapshot implements Serializable {
    @Id
    @Column(name = "stock_id")
    private Long id;

    @MapsId
    @Setter(AccessLevel.NONE)
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private Stock stock;

    @Column(precision = 15, scale = 2)
    private BigDecimal open;
    @Column(precision = 15, scale = 2)
    private BigDecimal high;
    @Column(precision = 15, scale = 2)
    private BigDecimal low;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal last;
    @Column(precision = 15, scale = 2)
    private BigDecimal close;
    @Column(precision = 15, scale = 2)
    private BigDecimal previousClose;

    @Column(precision = 15, scale = 2)
    private BigDecimal ask;
    @Column(precision = 15, scale = 2)
    private BigDecimal bid;
    @Column(precision = 15, scale = 2)
    private BigDecimal wowHigh; // Week-on-week high
    @Column(precision = 15, scale = 2)
    private BigDecimal momHigh; // Month-on-month high
    @Column(precision = 15, scale = 2)
    private BigDecimal wowLow;  // Week-on-week low
    @Column(precision = 15, scale = 2)
    private BigDecimal momLow;  // Month-on-month low

    @Column(precision = 15, scale = 2)
    private BigDecimal dailyChange;
    @Column(precision = 15, scale = 2)
    private BigDecimal weeklyChange;
    @Column(precision = 15, scale = 2)
    private BigDecimal monthlyChange;
    @Column(precision = 15, scale = 2)
    private BigDecimal yearlyChange;

    @Column(precision = 15, scale = 2)
    private BigDecimal dailyChangePercent;

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected StockSnapshot() {
    }

    protected StockSnapshot(Stock stock) {
        this.stock = stock;
    }
}
