package dev.canverse.stocks.investing.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.investing.domain.SecurityPostingRole;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SecurityPostingResponse(
        @NotNull UUID accountId,
        @NotNull UUID instrumentId,
        @NotNull String currency,
        @NotNull String quantityDelta,
        String unitPrice,
        String grossAmount,
        @NotNull SecurityPostingRole role,
        @NotNull Instant effectiveAt,
        @NotNull Long economicSequence,
        UUID reversesSecurityPostingId
) {}
