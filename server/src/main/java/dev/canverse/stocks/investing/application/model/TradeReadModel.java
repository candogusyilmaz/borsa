package dev.canverse.stocks.investing.application.model;

import dev.canverse.stocks.investing.domain.CalculationPolicy;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.investing.web.response.SecurityPostingResponse;
import dev.canverse.stocks.ledger.domain.FinancialAmount;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.web.response.PostingResponse;
import dev.canverse.stocks.reference.domain.InstrumentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TradeReadModel(UUID id, UUID accountId, String accountName, UUID instrumentId, String instrumentSymbol, String instrumentName,
        InstrumentType instrumentType, String currency, TradeSide side, FinancialAmount quantity, FinancialAmount unitPrice, FinancialAmount grossAmount,
        FinancialAmount commissionAmount, FinancialAmount cashDelta, FinancialAmount quantityDelta, Instant effectiveAt, Instant recordedAt,
        long economicSequence, RecordingMode recordingMode, PolicyDecision policyDecision, String sourceKind, CalculationPolicy calculationPolicy,
        List<PostingResponse> cashPostings, SecurityPostingResponse securityPosting, UUID reversalActivityId, String reversalReason, Instant reversedAt,
        UUID sourceImportBatchId, UUID sourceImportRowId, String sourceExternalId) {

    public TradeReadModel {
        cashPostings = List.copyOf(cashPostings);
    }
}
