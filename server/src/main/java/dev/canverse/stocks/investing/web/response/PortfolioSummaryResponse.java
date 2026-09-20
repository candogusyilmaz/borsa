package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.investing.application.model.PortfolioSummaryView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PortfolioSummaryResponse(@NotNull UUID id, @NotNull String name, @NotNull Integer accountCount, @NotNull Boolean archived, @NotNull Long version,
        @NotNull Instant createdAt, @NotNull Instant updatedAt, @Schema(nullable = true) Instant archivedAt) {

    public static PortfolioSummaryResponse from(PortfolioSummaryView view) {
        return new PortfolioSummaryResponse(view.id(), view.name(), view.accountCount(), view.archivedAt() != null, view.version(), view.createdAt(),
                view.updatedAt(), view.archivedAt());
    }
}
