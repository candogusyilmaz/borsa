package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.investing.domain.TradeImportFormat;
import dev.canverse.stocks.investing.domain.TradeImportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TradeImportPreviewResponse(
        @NotNull UUID id,
        @NotNull UUID accountId,
        @NotNull TradeImportFormat importFormat,
        @NotNull TradeImportStatus status,
        @NotNull String originalFileName,
        @NotNull String mediaType,
        @NotNull long byteSize,
        @NotNull String contentSha256,
        @NotNull int rowCount,
        @NotNull Instant createdAt,
        @Schema(nullable = true) Instant committedAt,
        @NotNull boolean commitEligible,
        @Schema(nullable = true) String previewToken,
        @NotNull List<TradeImportBatchIssueResponse> batchIssues,
        @NotNull TradeImportSummaryResponse summary,
        @NotNull List<TradeImportRowResponse> rows,
        @NotNull List<TradeImportPositionImpactResponse> positionImpacts
) {

    public TradeImportPreviewResponse {
        batchIssues = List.copyOf(batchIssues);
        rows = List.copyOf(rows);
        positionImpacts = List.copyOf(positionImpacts);
    }
}
