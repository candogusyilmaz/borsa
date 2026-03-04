package dev.canverse.stocks.service.instrument.model;

import dev.canverse.stocks.domain.entity.instrument.Instrument;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCustomInstrumentRequest(
        @NotEmpty
        String name,
        @NotEmpty
        String symbol,
        @NotNull
        Instrument.InstrumentType type,
        @NotEmpty
        @Size(min = 3, max = 3)
        String currencyCode
) {
}
