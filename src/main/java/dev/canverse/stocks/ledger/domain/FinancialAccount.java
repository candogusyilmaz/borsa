package dev.canverse.stocks.ledger.domain;

import dev.canverse.stocks.identity.domain.UserAccount;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "financial_account", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialAccount {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_account_id")
    private UserAccount ownerUserAccount;

    private String name;
    private String nameNormalized;

    @Enumerated(EnumType.STRING)
    private AccountKind accountKind;

    @Enumerated(EnumType.STRING)
    private TrackingMode trackingMode;

    @Enumerated(EnumType.STRING)
    private NegativeBalancePolicy negativeBalancePolicy;

    private String currencyCode;
    private String timeZone;
    private BigDecimal authorizedLimit;
    private UUID currentOpeningActivityId;
    private Instant archivedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private long version;

    public static FinancialAccount create(UUID id, UserAccount owner, String name, AccountKind accountKind, TrackingMode trackingMode, String currencyCode,
            String timeZone, NegativeBalancePolicy negativeBalancePolicy, FinancialAmount authorizedLimit, Instant observedAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(accountKind, "accountKind");
        Objects.requireNonNull(trackingMode, "trackingMode");
        Objects.requireNonNull(currencyCode, "currencyCode");
        var normalizedTimeZone = requireIanaTimeZone(timeZone);
        Objects.requireNonNull(observedAt, "observedAt");
        validateCapability(accountKind, trackingMode, negativeBalancePolicy, authorizedLimit);
        var displayName = normalizeName(name);

        var account = new FinancialAccount();
        account.id = id;
        account.ownerUserAccount = owner;
        account.name = displayName;
        account.nameNormalized = displayName.toUpperCase(Locale.ROOT);
        account.accountKind = accountKind;
        account.trackingMode = trackingMode;
        account.negativeBalancePolicy = negativeBalancePolicy;
        account.currencyCode = currencyCode;
        account.timeZone = normalizedTimeZone;
        account.authorizedLimit = authorizedLimit == null ? null : authorizedLimit.value();
        account.createdAt = observedAt;
        account.updatedAt = observedAt;
        return account;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isHoldingsOnly() {
        return trackingMode == TrackingMode.HOLDINGS_ONLY;
    }

    public boolean isFullLedger() {
        return trackingMode == TrackingMode.FULL_LEDGER;
    }

    public FinancialAmount authorizedLimitAmount() {
        return authorizedLimit == null ? null : FinancialAmount.of(authorizedLimit);
    }

    public void updateMetadata(String name, String timeZone, Instant observedAt) {
        this.name = normalizeName(name);
        this.nameNormalized = this.name.toUpperCase(Locale.ROOT);
        this.timeZone = requireIanaTimeZone(timeZone);
        this.updatedAt = Objects.requireNonNull(observedAt, "observedAt");
    }

    public void updatePolicy(NegativeBalancePolicy negativeBalancePolicy, FinancialAmount authorizedLimit, Instant observedAt) {
        validateCapability(accountKind, trackingMode, negativeBalancePolicy, authorizedLimit);
        this.negativeBalancePolicy = negativeBalancePolicy;
        this.authorizedLimit = authorizedLimit == null ? null : authorizedLimit.value();
        this.updatedAt = Objects.requireNonNull(observedAt, "observedAt");
    }

    public void archive(Instant observedAt) {
        if (archivedAt == null) {
            archivedAt = Objects.requireNonNull(observedAt, "observedAt");
            updatedAt = observedAt;
        }
    }

    public void setCurrentOpeningActivity(UUID activityId) {
        currentOpeningActivityId = Objects.requireNonNull(activityId, "activityId");
    }

    public static String requireIanaTimeZone(String timeZone) {
        var normalized = Objects.requireNonNull(timeZone, "timeZone").trim();
        if (!ZoneId.getAvailableZoneIds().contains(normalized)) {
            throw new IllegalArgumentException("timeZone must be an IANA region ID");
        }
        return normalized;
    }

    private static String normalizeName(String name) {
        var displayName = Objects.requireNonNull(name, "name").trim();
        if (displayName.isEmpty() || displayName.length() > 160) {
            throw new IllegalArgumentException("Account name must contain 1 to 160 characters");
        }
        return displayName;
    }

    private static void validateCapability(AccountKind kind, TrackingMode mode, NegativeBalancePolicy policy, FinancialAmount limit) {
        if (mode == TrackingMode.HOLDINGS_ONLY && !kind.supportsHoldingsOnly()) {
            throw new IllegalArgumentException("Only brokerage accounts support holdings-only tracking");
        }
        if (mode == TrackingMode.HOLDINGS_ONLY && (policy != null || limit != null)) {
            throw new IllegalArgumentException("Holdings-only accounts do not support cash policies");
        }
        if (mode == TrackingMode.FULL_LEDGER && kind.isAsset() && !kind.supportsNegativePolicy(policy)) {
            throw new IllegalArgumentException("Full-ledger asset accounts require a supported cash policy");
        }
        if (kind.isLiability() && (policy != null || limit != null)) {
            throw new IllegalArgumentException("Liability accounts do not support asset cash policies");
        }
        if (limit != null && (kind != AccountKind.CASH_CURRENT || policy != NegativeBalancePolicy.AUTHORIZED_LIMIT || !limit.isPositive())) {
            throw new IllegalArgumentException("Authorized limit is supported only for current cash accounts");
        }
        if (limit == null && policy == NegativeBalancePolicy.AUTHORIZED_LIMIT) {
            throw new IllegalArgumentException("Authorized limit is required");
        }
    }
}
