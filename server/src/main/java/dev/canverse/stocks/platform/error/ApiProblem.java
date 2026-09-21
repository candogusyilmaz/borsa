package dev.canverse.stocks.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record ApiProblem(
        @NotNull URI type,
        @NotNull String title,
        int status,
        @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) String detail,
        @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) URI instance,
        @NotNull String code,
        @NotNull String key,
        @NotNull String traceId,
        @NotNull Instant timestamp,
        @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> params
) {}
