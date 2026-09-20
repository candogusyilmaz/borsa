package dev.canverse.stocks.investing.web.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreatePortfolioRequest(@NotNull String name, @NotNull List<@NotNull UUID> accountIds) {}
