package dev.canverse.stocks.investing.web.response;

import jakarta.validation.constraints.NotNull;

public record TradeImportBatchIssueResponse(@NotNull String code, @NotNull String field, @NotNull String detail) {}
