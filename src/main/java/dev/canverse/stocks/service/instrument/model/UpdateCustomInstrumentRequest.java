package dev.canverse.stocks.service.instrument.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UpdateCustomInstrumentRequest(
        @NotEmpty
        String name,
        @NotEmpty
        String symbol,
        @NotEmpty
        @Size(min = 3, max = 3)
        String currencyCode
) {
}
