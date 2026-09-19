package dev.canverse.stocks.investing.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.investing.application.model.TradeReadModel;
import dev.canverse.stocks.investing.domain.CalculationPolicy;
import dev.canverse.stocks.investing.domain.TradeSide;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import dev.canverse.stocks.ledger.web.response.PostingResponse;
import dev.canverse.stocks.reference.domain.InstrumentType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TradeSummaryResponse(@NotNull UUID id, @NotNull UUID accountId, @NotNull String accountName, @NotNull UUID instrumentId,
        @NotNull String instrumentSymbol, @NotNull String instrumentName, @NotNull InstrumentType instrumentType, @NotNull String currency,
        @NotNull TradeSide side, @NotNull String quantity, @NotNull String unitPrice, @NotNull String grossAmount, @NotNull String commissionAmount,
        @NotNull String cashDelta, @NotNull String quantityDelta, @NotNull Instant effectiveAt, @NotNull Instant recordedAt, @NotNull Long economicSequence,
        @NotNull RecordingMode recordingMode, @NotNull PolicyDecision policyDecision, @NotNull String sourceKind, @NotNull CalculationPolicy calculationPolicy,
        @NotNull List<PostingResponse> cashPostings, @NotNull SecurityPostingResponse securityPosting, UUID reversalActivityId, String reversalReason,
        Instant reversedAt) {

    public TradeSummaryResponse {
        cashPostings = List.copyOf(cashPostings);
    }

    public static TradeSummaryResponse from(TradeReadModel model) {
        return new TradeSummaryResponse(model.id(), model.accountId(), model.accountName(), model.instrumentId(), model.instrumentSymbol(),
                model.instrumentName(), model.instrumentType(), model.currency(), model.side(), model.quantity().canonical(), model.unitPrice().canonical(),
                model.grossAmount().canonical(), model.commissionAmount().canonical(), model.cashDelta().canonical(), model.quantityDelta().canonical(),
                model.effectiveAt(), model.recordedAt(), model.economicSequence(), model.recordingMode(), model.policyDecision(), model.sourceKind(),
                model.calculationPolicy(), model.cashPostings(), model.securityPosting(), model.reversalActivityId(), model.reversalReason(),
                model.reversedAt());
    }
}
