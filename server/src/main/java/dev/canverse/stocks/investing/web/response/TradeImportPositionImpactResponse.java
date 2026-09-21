package dev.canverse.stocks.investing.web.response;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TradeImportPositionImpactResponse(
        @NotNull UUID instrumentId,
        @NotNull String symbol,
        @NotNull String currency,
        @NotNull long positionVersion,
        @NotNull String quantityBefore,
        @NotNull String quantityAfter,
        @NotNull String remainingBasisBefore,
        @NotNull String remainingBasisAfter,
        @NotNull String realizedEconomicPnlBefore,
        @NotNull String realizedEconomicPnlAfter
) {}
