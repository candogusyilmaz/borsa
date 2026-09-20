package dev.canverse.stocks.investing.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ArchivePortfolioRequest(@NotNull @PositiveOrZero Long version) {}
