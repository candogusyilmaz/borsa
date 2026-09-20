package dev.canverse.stocks.investing.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TradeImportRowIssueResponse(@NotNull String code, @NotNull String field, @NotNull String detail,
        @Schema(nullable = true) UUID relatedActivityId) {}
