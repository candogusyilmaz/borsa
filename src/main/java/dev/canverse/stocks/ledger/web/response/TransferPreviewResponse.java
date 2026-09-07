package dev.canverse.stocks.ledger.web.response;

import dev.canverse.stocks.ledger.domain.PolicyDecision;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferPreviewResponse(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull String currency,
        @NotNull String amount,
        @NotNull String sourceBefore,
        @NotNull String sourceAfter,
        @NotNull String destinationBefore,
        @NotNull String destinationAfter,
        @NotNull PolicyDecision sourceDecision,
        @NotNull PolicyDecision destinationDecision,
        long sourceVersion,
        long destinationVersion,
        boolean allowed) {}
