package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.investing.domain.TradeImportFormat;
import dev.canverse.stocks.investing.domain.TradeImportStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record TradeImportUploadResponse(
        @NotNull UUID id,
        @NotNull UUID accountId,
        @NotNull TradeImportFormat importFormat,
        @NotNull TradeImportStatus status,
        @NotNull boolean duplicateContent,
        @NotNull Instant createdAt,
        @NotNull String previewUrl
) {}
