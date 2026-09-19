package dev.canverse.stocks.investing.domain;

import dev.canverse.stocks.ledger.domain.FinancialAmount;
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
@Table(name = "security_posting", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityPosting {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private UUID activityId;
    private UUID financialAccountId;
    private UUID instrumentId;
    private String tradeCurrencyCode;
    private BigDecimal quantityDelta;
    private BigDecimal unitPrice;
    private BigDecimal grossAmount;

    @Enumerated(EnumType.STRING)
    private SecurityPostingRole postingRole;

    private Instant effectiveAt;
    private long economicSequence;
    private UUID reversesSecurityPostingId;
    private Instant createdAt;

    public static SecurityPosting buy(UUID id, UUID ownerUserAccountId, UUID activityId, UUID financialAccountId, UUID instrumentId, String tradeCurrencyCode,
            TradeSettlement trade, Instant effectiveAt, long economicSequence, Instant createdAt) {
        if (trade.side() != TradeSide.BUY) {
            throw new IllegalArgumentException("Buy security posting requires a buy settlement");
        }
        return create(id, ownerUserAccountId, activityId, financialAccountId, instrumentId, tradeCurrencyCode, trade.quantityDelta(), trade.unitPrice(),
                trade.grossAmount(), SecurityPostingRole.BUY, effectiveAt, economicSequence, null, createdAt);
    }

    public static SecurityPosting sell(UUID id, UUID ownerUserAccountId, UUID activityId, UUID financialAccountId, UUID instrumentId, String tradeCurrencyCode,
            TradeSettlement trade, Instant effectiveAt, long economicSequence, Instant createdAt) {
        if (trade.side() != TradeSide.SELL) {
            throw new IllegalArgumentException("Sell security posting requires a sell settlement");
        }
        return create(id, ownerUserAccountId, activityId, financialAccountId, instrumentId, tradeCurrencyCode, trade.quantityDelta(), trade.unitPrice(),
                trade.grossAmount(), SecurityPostingRole.SELL, effectiveAt, economicSequence, null, createdAt);
    }

    public static SecurityPosting reversal(UUID id, UUID ownerUserAccountId, UUID reversalActivityId, SecurityPosting original, Instant createdAt) {
        if (original.postingRole == SecurityPostingRole.REVERSAL) {
            throw new IllegalArgumentException("A security reversal cannot reverse another reversal");
        }
        return create(id, ownerUserAccountId, reversalActivityId, original.financialAccountId, original.instrumentId, original.tradeCurrencyCode,
                FinancialAmount.of(original.quantityDelta).negate(), null, null, SecurityPostingRole.REVERSAL, original.effectiveAt, original.economicSequence,
                original.id, createdAt);
    }

    private static SecurityPosting create(UUID id, UUID ownerUserAccountId, UUID activityId, UUID financialAccountId, UUID instrumentId,
            String tradeCurrencyCode, FinancialAmount quantityDelta, FinancialAmount unitPrice, FinancialAmount grossAmount, SecurityPostingRole postingRole,
            Instant effectiveAt, long economicSequence, UUID reversesSecurityPostingId, Instant createdAt) {
        var posting = new SecurityPosting();
        posting.id = Objects.requireNonNull(id, "id");
        posting.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        posting.activityId = Objects.requireNonNull(activityId, "activityId");
        posting.financialAccountId = Objects.requireNonNull(financialAccountId, "financialAccountId");
        posting.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        posting.tradeCurrencyCode = Objects.requireNonNull(tradeCurrencyCode, "tradeCurrencyCode");
        posting.quantityDelta = Objects.requireNonNull(quantityDelta, "quantityDelta").value();
        posting.unitPrice = unitPrice == null ? null : unitPrice.value();
        posting.grossAmount = grossAmount == null ? null : grossAmount.value();
        posting.postingRole = Objects.requireNonNull(postingRole, "postingRole");
        posting.effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt");
        if (economicSequence < 0) {
            throw new IllegalArgumentException("economicSequence must be non-negative");
        }
        posting.economicSequence = economicSequence;
        posting.reversesSecurityPostingId = reversesSecurityPostingId;
        posting.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return posting;
    }

}
