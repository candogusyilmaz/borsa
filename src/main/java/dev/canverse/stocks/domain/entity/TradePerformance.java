package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "trade_performance")
public class TradePerformance {
    @Id
    @Column(name = "trade_id")
    private Long id;

    @MapsId
    @Setter(AccessLevel.NONE)
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private Trade trade;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal profit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal returnPercentage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerformanceCategory performanceCategory;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TradePerformance() {
    }

    protected TradePerformance(Trade trade, BigDecimal profit, BigDecimal returnPercentage) {
        this.trade = trade;
        this.profit = profit;
        this.returnPercentage = returnPercentage;
        this.performanceCategory = calculatePerformanceCategory(returnPercentage);
    }

    public enum PerformanceCategory {
        EXCELLENT,  // > 20%
        GOOD,       // 10-20%
        MODERATE,   // 0-10%
        POOR,       // < 0%
    }

    private PerformanceCategory calculatePerformanceCategory(BigDecimal returnPercentage) {
        if (returnPercentage.compareTo(BigDecimal.valueOf(0.20)) > 0) {
            return PerformanceCategory.EXCELLENT;
        } else if (returnPercentage.compareTo(BigDecimal.valueOf(0.10)) >= 0) {
            return PerformanceCategory.GOOD;
        } else if (returnPercentage.compareTo(BigDecimal.ZERO) >= 0) {
            return PerformanceCategory.MODERATE;
        } else {
            return PerformanceCategory.POOR;
        }
    }
}
