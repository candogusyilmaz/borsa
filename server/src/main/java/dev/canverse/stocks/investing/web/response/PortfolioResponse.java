package dev.canverse.stocks.investing.web.response;

import dev.canverse.stocks.investing.application.model.PortfolioSummaryView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PortfolioResponse(@NotNull UUID id, @NotNull String name, @NotNull Integer accountCount, @NotNull Boolean archived, @NotNull Long version,
        @NotNull Instant createdAt, @NotNull Instant updatedAt, @Schema(nullable = true) Instant archivedAt, @NotNull List<PortfolioAccountResponse> accounts) {

    public PortfolioResponse {
        accounts = List.copyOf(Objects.requireNonNull(accounts, "accounts"));
        if (accountCount != accounts.size()) {
            throw new IllegalArgumentException("Portfolio accountCount must match the account list size");
        }
    }

    public static PortfolioResponse from(PortfolioSummaryView view, List<PortfolioAccountResponse> accounts) {
        return new PortfolioResponse(view.id(), view.name(), accounts.size(), view.archivedAt() != null, view.version(), view.createdAt(), view.updatedAt(),
                view.archivedAt(), accounts);
    }
}
