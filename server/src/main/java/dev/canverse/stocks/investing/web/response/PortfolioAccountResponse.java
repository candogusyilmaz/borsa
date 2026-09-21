package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.ledger.domain.AccountKind;
import dev.canverse.stocks.ledger.domain.TrackingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PortfolioAccountResponse(
        @NotNull UUID id,
        @NotNull String name,
        @NotNull AccountKind kind,
        @NotNull TrackingMode trackingMode,
        @NotNull String currency,
        @NotNull Boolean archived,
        @Schema(nullable = true) Instant archivedAt
) {}
