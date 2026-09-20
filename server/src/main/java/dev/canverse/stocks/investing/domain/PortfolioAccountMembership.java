package dev.canverse.stocks.investing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_account_membership", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioAccountMembership {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_user_account_id", nullable = false, updatable = false)
    private UUID ownerUserAccountId;

    @Column(name = "portfolio_id", nullable = false, updatable = false)
    private UUID portfolioId;

    @Column(name = "financial_account_id", nullable = false, updatable = false)
    private UUID financialAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PortfolioAccountMembership create(UUID id, UUID ownerUserAccountId, UUID portfolioId, UUID financialAccountId, Instant createdAt) {
        var membership = new PortfolioAccountMembership();
        membership.id = Objects.requireNonNull(id, "id");
        membership.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        membership.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId");
        membership.financialAccountId = Objects.requireNonNull(financialAccountId, "financialAccountId");
        membership.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return membership;
    }
}
