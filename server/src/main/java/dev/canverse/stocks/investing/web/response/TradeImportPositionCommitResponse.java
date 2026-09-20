package dev.canverse.stocks.investing.web.response;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TradeImportPositionCommitResponse(@NotNull UUID instrumentId, @NotNull long positionVersion) {}
