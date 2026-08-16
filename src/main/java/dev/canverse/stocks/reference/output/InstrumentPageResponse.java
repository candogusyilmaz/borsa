package dev.canverse.stocks.reference.output;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record InstrumentPageResponse(@NotNull List<InstrumentSummaryResponse> instruments, String nextCursor) {

    public InstrumentPageResponse {
        instruments = List.copyOf(instruments);
    }

    public static InstrumentPageResponse from(List<InstrumentSummaryResponse> instruments, String nextCursor) {
        return new InstrumentPageResponse(instruments, nextCursor);
    }
}
