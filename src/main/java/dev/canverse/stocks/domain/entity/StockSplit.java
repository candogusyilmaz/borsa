package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "stock_splits")
public class StockSplit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal ratio;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    @Setter
    @Column(nullable = false)
    private boolean processed;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected StockSplit() {
    }

    public StockSplit(Stock stock, BigDecimal ratio, LocalDate effectiveDate) {
        this.stock = stock;
        this.ratio = ratio;
        this.effectiveDate = effectiveDate;
        this.processed = false;
    }
}
