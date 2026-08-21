package dev.canverse.stocks.ledger.web.response;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ActivityPageResponse(@NotNull List<ActivityResponse> activities, String nextCursor) {
    public ActivityPageResponse {
        activities = List.copyOf(activities);
    }
}
