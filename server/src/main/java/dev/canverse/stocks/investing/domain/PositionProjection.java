package dev.canverse.stocks.investing.domain;

import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.ProjectionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
@Table(name = "position_projection", schema = "ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionProjection {

    @Id
    private UUID id;

    private UUID ownerUserAccountId;
    private UUID financialAccountId;
    private UUID instrumentId;
    private String currencyCode;
    private BigDecimal currentQuantity;
    private BigDecimal remainingEconomicBasis;
    private BigDecimal cumulativeRealizedEconomicPnl;
    @Enumerated(EnumType.STRING)
    private CalculationPolicy calculationPolicy;

    @Enumerated(EnumType.STRING)
    private ProjectionStatus projectionStatus;

    private Instant asOf;
    private UUID inputWatermarkActivityId;
    private Instant lastSuccessfulBuildAt;
    private Instant staleFrom;
    private Instant updatedAt;

    @Version
    private long version;

    public static PositionProjection create(UUID id, UUID ownerUserAccountId, UUID financialAccountId, UUID instrumentId, String currencyCode,
            TradeReplayResult replay, Instant builtAt) {
        var projection = new PositionProjection();
        projection.id = Objects.requireNonNull(id, "id");
        projection.ownerUserAccountId = Objects.requireNonNull(ownerUserAccountId, "ownerUserAccountId");
        projection.financialAccountId = Objects.requireNonNull(financialAccountId, "financialAccountId");
        projection.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        projection.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
        projection.calculationPolicy = CalculationPolicy.WEIGHTED_AVERAGE_ECONOMIC_V1;
        projection.applySuccessfulReplay(replay, builtAt);
        return projection;
    }

    public void markStale(Instant invalidatedFrom, Instant markedAt) {
        Objects.requireNonNull(invalidatedFrom, "invalidatedFrom");
        Objects.requireNonNull(markedAt, "markedAt");
        if (projectionStatus != ProjectionStatus.CURRENT && projectionStatus != ProjectionStatus.STALE && projectionStatus != ProjectionStatus.REBUILDING &&
                projectionStatus != ProjectionStatus.FAILED) {
            throw new IllegalStateException("Only a built position projection can become stale");
        }

        staleFrom = staleFrom == null || invalidatedFrom.isBefore(staleFrom) ? invalidatedFrom : staleFrom;
        projectionStatus = ProjectionStatus.STALE;
        updatedAt = markedAt;
    }

    public void beginRebuild(Instant startedAt) {
        Objects.requireNonNull(startedAt, "startedAt");
        if ((projectionStatus != ProjectionStatus.STALE && projectionStatus != ProjectionStatus.FAILED) || staleFrom == null) {
            throw new IllegalStateException("A position projection must be stale or failed before rebuilding");
        }

        projectionStatus = ProjectionStatus.REBUILDING;
        updatedAt = startedAt;
    }

    public void completeRebuild(TradeReplayResult replay, Instant builtAt) {
        Objects.requireNonNull(builtAt, "builtAt");
        if (projectionStatus != ProjectionStatus.REBUILDING || staleFrom == null) {
            throw new IllegalStateException("A position projection must be rebuilding before completion");
        }

        applySuccessfulReplay(replay, builtAt);
    }

    public void failRebuild(Instant failedAt) {
        Objects.requireNonNull(failedAt, "failedAt");
        if (projectionStatus != ProjectionStatus.REBUILDING || staleFrom == null) {
            throw new IllegalStateException("A position projection must be rebuilding before failure");
        }

        projectionStatus = ProjectionStatus.FAILED;
        updatedAt = failedAt;
    }

    private void applySuccessfulReplay(TradeReplayResult replay, Instant builtAt) {
        var result = Objects.requireNonNull(replay, "replay");
        var state = result.state();
        if (result.asOf() == null || result.watermarkActivityId() == null) {
            throw new IllegalArgumentException("A persisted position projection requires an input fact");
        }
        currentQuantity = state.quantity().value();
        remainingEconomicBasis = state.remainingBasis().value();
        cumulativeRealizedEconomicPnl = state.realizedEconomicPnl().value();
        projectionStatus = ProjectionStatus.CURRENT;
        asOf = result.asOf();
        inputWatermarkActivityId = result.watermarkActivityId();
        lastSuccessfulBuildAt = Objects.requireNonNull(builtAt, "builtAt");
        staleFrom = null;
        updatedAt = builtAt;
    }

    public FinancialAmount quantity() {
        return FinancialAmount.of(currentQuantity);
    }

    public FinancialAmount remainingBasis() {
        return FinancialAmount.of(remainingEconomicBasis);
    }

    public FinancialAmount realizedEconomicPnl() {
        return FinancialAmount.of(cumulativeRealizedEconomicPnl);
    }
}
