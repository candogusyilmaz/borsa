package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TradeImportRowResponse(@NotNull UUID id, @NotNull int sourceRecordNumber, @NotNull List<String> sourceValues,
        @Schema(nullable = true) String externalId, @Schema(nullable = true) TradeSide side, @Schema(nullable = true) UUID instrumentId,
        @Schema(nullable = true) String currency, @Schema(nullable = true) Instant effectiveAt, @Schema(nullable = true) Long economicSequence,
        @Schema(nullable = true) String quantity, @Schema(nullable = true) String unitPrice, @Schema(nullable = true) String commissionAmount,
        @Schema(nullable = true) String grossAmount, @Schema(nullable = true) String cashDelta, @Schema(nullable = true) String rowFingerprint,
        @Schema(nullable = true) PolicyDecision policyDecision, @Schema(nullable = true) UUID committedActivityId,
        @NotNull List<TradeImportRowIssueResponse> issues) {

    public TradeImportRowResponse {
        sourceValues = List.copyOf(sourceValues);
        issues = List.copyOf(issues);
    }
}
