package dev.canverse.stocks.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ValidationError(@NotNull String field, @NotNull String key, @NotNull String detail,
        @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> params) {}
