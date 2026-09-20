package dev.canverse.stocks.investing.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record UpdatePortfolioRequest(@NotNull String name, @NotNull List<@NotNull UUID> accountIds, @NotNull @PositiveOrZero Long version) {}
