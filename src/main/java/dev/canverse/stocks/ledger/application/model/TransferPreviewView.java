package dev.canverse.stocks.ledger.application.model;

import dev.canverse.stocks.ledger.domain.PolicyDecision;
import java.util.UUID;

public record TransferPreviewView(
        UUID sourceAccountId,
        UUID destinationAccountId,
        String currencyCode,
        String amount,
        String sourceBefore,
        String sourceAfter,
        String destinationBefore,
        String destinationAfter,
        PolicyDecision sourceDecision,
        PolicyDecision destinationDecision,
        long sourceVersion,
        long destinationVersion,
        boolean allowed) {}
