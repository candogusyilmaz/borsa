package dev.canverse.stocks.ledger.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReversalRequest(@NotNull UUID clientRequestId, @NotBlank @Size(max = 500) String correctionReason) {}
