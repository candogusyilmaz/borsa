package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.investing.domain.CalculationPolicy;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record TradePreviewResponse(
        @NotNull UUID accountId,
        @NotNull UUID instrumentId,
        @NotNull String instrumentSymbol,
        @NotNull TradeSide side,
        @NotNull String quantity,
        @NotNull String unitPrice,
        @NotNull String commissionAmount,
        @NotNull String grossAmount,
        @NotNull String currency,
        @NotNull RecordingMode recordingMode,
        @NotNull Instant effectiveAt,
        @NotNull Long economicSequence,
        @NotNull String cashDelta,
        @NotNull String cashBalanceBefore,
        @NotNull String cashBalanceAfter,
        @NotNull PolicyDecision policyDecision,
        @NotNull Boolean allowed,
        @NotNull String quantityBefore,
        @NotNull String quantityAfter,
        @NotNull String remainingBasisBefore,
        @NotNull String remainingBasisAfter,
        @NotNull String realizedEconomicPnlBefore,
        @NotNull String realizedEconomicPnlAfter,
        String allocatedBasis,
        String realizedEconomicPnl,
        @NotNull CalculationPolicy calculationPolicy,
        @NotNull Long cashBalanceVersion,
        @NotNull Long positionVersion
) {}
