package dev.canverse.stocks.domain.entity.portfolio;

import dev.canverse.stocks.domain.entity.Currency;
import dev.canverse.stocks.domain.entity.account.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(schema = "portfolio", name = "dashboards")
public class Dashboard implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @NotEmpty
    @Column(nullable = false)
    private String name;

    @Setter
    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Currency currency;

    @Setter
    @NotNull
    @Column(nullable = false)
    private boolean isDefault = false;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DashboardPortfolio> dashboardPortfolios = new ArrayList<>();

    protected Dashboard() {
    }

    public Dashboard(User user, String name, Currency currency) {
        this.user = user;
        this.name = name;
        this.currency = currency;
    }

    public List<Portfolio> getPortfolios() {
        return dashboardPortfolios.stream()
                .map(DashboardPortfolio::getPortfolio)
                .toList();
    }

    public void addPortfolio(Portfolio portfolio) {
        if (getPortfolios().contains(portfolio)) {
            return;
        }

        var dashboardPortfolio = new DashboardPortfolio(this, portfolio);
        dashboardPortfolios.add(dashboardPortfolio);
    }
}
