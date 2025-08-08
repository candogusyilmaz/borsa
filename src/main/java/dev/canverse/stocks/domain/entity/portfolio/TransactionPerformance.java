package dev.canverse.stocks.domain.entity.portfolio;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(schema = "portfolio", name = "transaction_performance")
public class TransactionPerformance {
    @Id
    @Column(name = "trade_id")
    private Long id;

    @MapsId
    @Setter(AccessLevel.NONE)
    @JsonBackReference
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private Transaction transaction;

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

    protected TransactionPerformance() {
    }

    protected TransactionPerformance(Transaction transaction, BigDecimal profit, BigDecimal returnPercentage) {
        this.transaction = transaction;
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
