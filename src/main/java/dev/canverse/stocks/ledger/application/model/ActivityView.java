package dev.canverse.stocks.ledger.application.model;

import dev.canverse.stocks.ledger.domain.ActivityType;
import dev.canverse.stocks.ledger.domain.PolicyDecision;
import dev.canverse.stocks.ledger.domain.RecordingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActivityView(
        UUID id,
        ActivityType activityType,
        RecordingMode recordingMode,
        Instant effectiveAt,
        Instant recordedAt,
        PolicyDecision policyDecision,
        String sourceKind,
        UUID reversesActivityId,
        UUID supersedesActivityId,
        List<PostingView> postings) {}
