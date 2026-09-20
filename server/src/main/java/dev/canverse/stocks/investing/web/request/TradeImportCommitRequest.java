package dev.canverse.stocks.investing.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record TradeImportCommitRequest(@NotNull UUID clientRequestId, @NotNull @Pattern(regexp = "[0-9a-f]{64}") String previewToken) {}
