package dev.canverse.stocks.service.instrument.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstrumentInfo {
    @NotNull
    private String id;
    @NotNull
    private String symbol;
    @NotNull
    private String name;
    @NotNull
    private String[] supportedCurrencies;
}
