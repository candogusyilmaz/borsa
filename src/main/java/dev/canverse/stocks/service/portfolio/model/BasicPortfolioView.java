package dev.canverse.stocks.service.portfolio.model;

import jakarta.validation.constraints.NotNull;

public record BasicPortfolioView(@NotNull String id, @NotNull String name) {

}
