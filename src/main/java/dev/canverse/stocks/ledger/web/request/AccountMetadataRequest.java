package dev.canverse.stocks.ledger.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AccountMetadataRequest(@NotNull UUID clientRequestId, @NotBlank @Size(max = 160) String name, @NotBlank @Size(max = 120) String timeZone,
        @NotNull @PositiveOrZero Long version) {}
