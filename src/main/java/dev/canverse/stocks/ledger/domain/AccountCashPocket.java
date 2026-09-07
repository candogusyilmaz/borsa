package dev.canverse.stocks.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_cash_pocket", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountCashPocket {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_account_id")
    private FinancialAccount financialAccount;

    private String currencyCode;

    @Enumerated(EnumType.STRING)
    private CoverageStatus coverageStatus;

    private Instant coverageFrom;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private long version;

    public static AccountCashPocket create(UUID id, UUID ownerUserAccountId, FinancialAccount financialAccount, String currencyCode, Instant coverageFrom,
            Instant observedAt) {
        var pocket = new AccountCashPocket();
        pocket.id = Objects.requireNonNull(id, "id");
        pocket.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        pocket.financialAccount = Objects.requireNonNull(financialAccount, "financialAccount");
        pocket.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        pocket.coverageStatus = CoverageStatus.KNOWN_FROM_OPENING;
        pocket.coverageFrom = Objects.requireNonNull(coverageFrom, "coverageFrom");
        pocket.createdAt = Objects.requireNonNull(observedAt, "observedAt");
        pocket.updatedAt = observedAt;
        return pocket;
    }
}
