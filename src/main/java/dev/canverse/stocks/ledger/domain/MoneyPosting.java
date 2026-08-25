package dev.canverse.stocks.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "money_posting", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyPosting {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private UUID activityId;
    private UUID financialAccountId;
    private UUID cashPocketId;
    private String currencyCode;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PostingRole postingRole;

    private Instant createdAt;

    public static MoneyPosting opening(
            UUID id,
            UUID ownerUserAccountId,
            UUID activityId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            FinancialAmount amount,
            Instant createdAt) {
        return create(
                id,
                ownerUserAccountId,
                activityId,
                financialAccountId,
                cashPocketId,
                currencyCode,
                amount,
                PostingRole.OPENING,
                createdAt);
    }

    public static MoneyPosting deposit(
            UUID id,
            UUID ownerUserAccountId,
            UUID activityId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            FinancialAmount amount,
            Instant createdAt) {
        requirePositive(amount, PostingRole.DEPOSIT);
        return create(
                id,
                ownerUserAccountId,
                activityId,
                financialAccountId,
                cashPocketId,
                currencyCode,
                amount,
                PostingRole.DEPOSIT,
                createdAt);
    }

    public static MoneyPosting withdrawal(
            UUID id,
            UUID ownerUserAccountId,
            UUID activityId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            FinancialAmount amount,
            Instant createdAt) {
        requireNegative(amount, PostingRole.WITHDRAWAL);
        return create(
                id,
                ownerUserAccountId,
                activityId,
                financialAccountId,
                cashPocketId,
                currencyCode,
                amount,
                PostingRole.WITHDRAWAL,
                createdAt);
    }

    public static MoneyPosting transferSource(
            UUID id,
            UUID ownerUserAccountId,
            UUID activityId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            FinancialAmount amount,
            Instant createdAt) {
        requireNegative(amount, PostingRole.TRANSFER_SOURCE);
        return create(
                id,
                ownerUserAccountId,
                activityId,
                financialAccountId,
                cashPocketId,
                currencyCode,
                amount,
                PostingRole.TRANSFER_SOURCE,
                createdAt);
    }

    public static MoneyPosting transferDestination(
            UUID id,
            UUID ownerUserAccountId,
            UUID activityId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            FinancialAmount amount,
            Instant createdAt) {
        requirePositive(amount, PostingRole.TRANSFER_DESTINATION);
        return create(
                id,
                ownerUserAccountId,
                activityId,
                financialAccountId,
                cashPocketId,
                currencyCode,
                amount,
                PostingRole.TRANSFER_DESTINATION,
                createdAt);
    }

    public static MoneyPosting reversal(
            UUID id,
            UUID ownerUserAccountId,
            UUID activityId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            FinancialAmount amount,
            Instant createdAt) {
        return create(
                id,
                ownerUserAccountId,
                activityId,
                financialAccountId,
                cashPocketId,
                currencyCode,
                amount,
                PostingRole.REVERSAL,
                createdAt);
    }

    public static MoneyPosting adjustment(
            UUID id,
            UUID ownerUserAccountId,
            UUID activityId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            FinancialAmount amount,
            Instant createdAt) {
        if (Objects.requireNonNull(amount, "amount").isZero()) {
            throw new IllegalArgumentException("ADJUSTMENT posting amount must be non-zero");
        }
        return create(
                id,
                ownerUserAccountId,
                activityId,
                financialAccountId,
                cashPocketId,
                currencyCode,
                amount,
                PostingRole.ADJUSTMENT,
                createdAt);
    }

    private static MoneyPosting create(
            UUID id,
            UUID ownerUserAccountId,
            UUID activityId,
            UUID financialAccountId,
            UUID cashPocketId,
            String currencyCode,
            FinancialAmount amount,
            PostingRole postingRole,
            Instant createdAt) {
        var posting = new MoneyPosting();
        posting.id = Objects.requireNonNull(id, "id");
        posting.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        posting.activityId = Objects.requireNonNull(activityId, "activityId");
        posting.financialAccountId = Objects.requireNonNull(financialAccountId, "financialAccountId");
        posting.cashPocketId = Objects.requireNonNull(cashPocketId, "cashPocketId");
        posting.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        posting.amount = Objects.requireNonNull(amount, "amount").value();
        posting.postingRole = Objects.requireNonNull(postingRole, "postingRole");
        posting.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return posting;
    }

    private static void requirePositive(FinancialAmount amount, PostingRole postingRole) {
        if (!Objects.requireNonNull(amount, "amount").isPositive()) {
            throw new IllegalArgumentException(postingRole + " posting amount must be positive");
        }
    }

    private static void requireNegative(FinancialAmount amount, PostingRole postingRole) {
        if (!Objects.requireNonNull(amount, "amount").isNegative()) {
            throw new IllegalArgumentException(postingRole + " posting amount must be negative");
        }
    }
}
