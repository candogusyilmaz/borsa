package dev.canverse.stocks.identity.output;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;

public record DeviceSessionPageResponse(@NotNull List<DeviceSessionResponse> sessions, String nextCursor) {

    public DeviceSessionPageResponse {
        Objects.requireNonNull(sessions, "sessions");
        sessions = List.copyOf(sessions);
    }
}
