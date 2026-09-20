package dev.canverse.stocks.investing.domain;

import dev.canverse.stocks.ledger.domain.FinancialAmount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trade_import_row", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeImportRow {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private UUID importBatchId;
    private int sourceRecordNumber;
    private String sourceExternalId;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> sourceValues;

    @Enumerated(EnumType.STRING)
    private TradeImportNormalizationStatus normalizationStatus;

    @Enumerated(EnumType.STRING)
    private TradeSide side;

    private UUID instrumentId;
    private String currencyCode;
    private Instant effectiveAt;
    private Long economicSequence;
    @Column(precision = 38, scale = 18)
    private BigDecimal quantity;

    @Column(precision = 38, scale = 18)
    private BigDecimal unitPrice;

    @Column(precision = 38, scale = 18)
    private BigDecimal commissionAmount;

    @Column(precision = 38, scale = 18)
    private BigDecimal grossAmount;

    @Column(precision = 38, scale = 18)
    private BigDecimal cashDelta;
    private String rowFingerprint;
    private Instant createdAt;

    public static TradeImportRow create(UUID id, UUID ownerUserAccountId, UUID importBatchId, int sourceRecordNumber, String sourceExternalId,
            List<String> sourceValues, TradeImportNormalizationStatus normalizationStatus, TradeSide side, UUID instrumentId, String currencyCode,
            Instant effectiveAt, Long economicSequence, FinancialAmount quantity, FinancialAmount unitPrice, FinancialAmount commissionAmount,
            FinancialAmount grossAmount, FinancialAmount cashDelta, String rowFingerprint, Instant createdAt) {
        var row = new TradeImportRow();
        row.id = Objects.requireNonNull(id, "id");
        row.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        row.importBatchId = Objects.requireNonNull(importBatchId, "importBatchId");
        row.sourceRecordNumber = sourceRecordNumber;
        row.sourceExternalId = sourceExternalId;
        row.sourceValues = List.copyOf(Objects.requireNonNull(sourceValues, "sourceValues"));
        row.normalizationStatus = Objects.requireNonNull(normalizationStatus, "normalizationStatus");
        row.side = side;
        row.instrumentId = instrumentId;
        row.currencyCode = currencyCode;
        row.effectiveAt = effectiveAt;
        row.economicSequence = economicSequence;
        row.quantity = value(quantity);
        row.unitPrice = value(unitPrice);
        row.commissionAmount = value(commissionAmount);
        row.grossAmount = value(grossAmount);
        row.cashDelta = value(cashDelta);
        row.rowFingerprint = rowFingerprint;
        row.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return row;
    }

    public boolean isValid() {
        return normalizationStatus == TradeImportNormalizationStatus.VALID;
    }

    private static BigDecimal value(FinancialAmount amount) {
        return amount == null ? null : amount.value();
    }
}
