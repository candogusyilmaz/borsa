package dev.canverse.stocks.investing.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TradeImportSummaryResponse(@NotNull int rowCount, @NotNull int normalizedRowCount, @NotNull int issueRowCount, @NotNull int duplicateRowCount,
        @NotNull int buyCount, @NotNull int sellCount, @NotNull String currency, @NotNull String buyGross, @NotNull String sellGross,
        @NotNull String commissionTotal, @NotNull String cashDeltaTotal, @Schema(nullable = true) String cashBalanceBefore,
        @Schema(nullable = true) String cashBalanceAfter, @Schema(nullable = true) Long accountVersion, @Schema(nullable = true) Long cashBalanceVersion) {}
