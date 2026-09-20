package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.investing.domain.TradeImportStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TradeImportCommitResponse(@NotNull UUID id, @NotNull UUID accountId, @NotNull TradeImportStatus status, @NotNull int committedRowCount,
        @NotNull List<UUID> activityIds, @NotNull String cashBalanceAfter, @NotNull long cashBalanceVersion,
        @NotNull List<TradeImportPositionCommitResponse> positions, @NotNull Instant committedAt) {

    public TradeImportCommitResponse {
        activityIds = List.copyOf(activityIds);
        positions = List.copyOf(positions);
    }
}
