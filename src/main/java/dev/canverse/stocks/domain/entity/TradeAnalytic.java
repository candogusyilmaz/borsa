package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "trade_analytics")
public class TradeAnalytic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
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

    protected TradeAnalytic() {
    }

    public TradeAnalytic(Trade trade, BigDecimal profit, BigDecimal returnPercentage) {
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
        if (returnPercentage.compareTo(BigDecimal.valueOf(20)) > 0) {
            return PerformanceCategory.EXCELLENT;
        } else if (returnPercentage.compareTo(BigDecimal.valueOf(10)) >= 0) {
            return PerformanceCategory.GOOD;
        } else if (returnPercentage.compareTo(BigDecimal.ZERO) >= 0) {
            return PerformanceCategory.MODERATE;
        } else {
            return PerformanceCategory.POOR;
        }
    }
}
