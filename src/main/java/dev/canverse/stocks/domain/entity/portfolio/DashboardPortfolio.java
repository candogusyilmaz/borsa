package dev.canverse.stocks.domain.entity.portfolio;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Entity
@Table(schema = "portfolio", name = "dashboard_portfolios")
public class DashboardPortfolio implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Dashboard dashboard;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Portfolio portfolio;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected DashboardPortfolio() {
    }

    public DashboardPortfolio(Dashboard dashboard, Portfolio portfolio) {
        this.dashboard = dashboard;
        this.portfolio = portfolio;
    }
}
