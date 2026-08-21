package dev.canverse.stocks.ledger.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.canverse.stocks.ledger.application.model.ActivityView;
import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityResponse(
        @NotNull UUID id,
        @NotNull ActivityType activityType,
        @NotNull RecordingMode recordingMode,
        @NotNull Instant effectiveAt,
        @NotNull Instant recordedAt,
        @NotNull PolicyDecision policyDecision,
        @NotNull String sourceKind,
        UUID reversesActivityId,
        UUID supersedesActivityId,
        @NotNull List<PostingResponse> postings) {

    public ActivityResponse {
        postings = List.copyOf(postings);
    }

    public static ActivityResponse from(ActivityView view) {
        return new ActivityResponse(
                view.id(),
                view.activityType(),
                view.recordingMode(),
                view.effectiveAt(),
                view.recordedAt(),
                view.policyDecision(),
                view.sourceKind(),
                view.reversesActivityId(),
                view.supersedesActivityId(),
                view.postings().stream().map(PostingResponse::from).toList());
    }
}
