package dev.canverse.stocks.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_balance_projection", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBalanceProjection {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_account_id")
    private FinancialAccount financialAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_pocket_id")
    private AccountCashPocket cashPocket;

    private String currencyCode;
    private BigDecimal ledgerBalance;
    private Instant lastAppliedRecordedAt;
    private UUID lastAppliedActivityId;
    private Instant updatedAt;

    @Version
    private long version;

    public static AccountBalanceProjection create(
            UUID id,
            UUID ownerUserAccountId,
            FinancialAccount financialAccount,
            AccountCashPocket cashPocket,
            String currencyCode,
            FinancialAmount openingBalance,
            Instant recordedAt,
            UUID activityId,
            Instant observedAt) {
        var projection = new AccountBalanceProjection();
        projection.id = Objects.requireNonNull(id, "id");
        projection.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        projection.financialAccount = Objects.requireNonNull(financialAccount, "financialAccount");
        projection.cashPocket = Objects.requireNonNull(cashPocket, "cashPocket");
        projection.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        projection.ledgerBalance =
                Objects.requireNonNull(openingBalance, "openingBalance").value();
        projection.lastAppliedRecordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        projection.lastAppliedActivityId = Objects.requireNonNull(activityId, "activityId");
        projection.updatedAt = Objects.requireNonNull(observedAt, "observedAt");
        return projection;
    }

    public void apply(FinancialAmount delta, Instant recordedAt, UUID activityId, Instant observedAt) {
        ledgerBalance = ledgerBalance.add(Objects.requireNonNull(delta, "delta").value());
        lastAppliedRecordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        lastAppliedActivityId = Objects.requireNonNull(activityId, "activityId");
        updatedAt = Objects.requireNonNull(observedAt, "observedAt");
    }

    public FinancialAmount balance() {
        return FinancialAmount.of(ledgerBalance);
    }
}
