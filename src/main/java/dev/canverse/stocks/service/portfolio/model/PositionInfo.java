package dev.canverse.stocks.service.portfolio.model;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class PositionInfo {
    @NotNull
    private String id;
    @NotNull
    private InstrumentView instrument;
    @NotNull
    private PortfolioView portfolio;
    @NotNull
    private BigDecimal quantity;
    @NotNull
    private BigDecimal total;
    @NotNull
    private BigDecimal avgCost;

    @Getter
    @Setter
    public static class InstrumentView {
        @NotNull
        private String id;
        @NotNull
        private String name;
        @NotNull
        private String symbol;
        @NotNull
        private String currency;
        private BigDecimal last;
        private BigDecimal dailyChange;
        private Instant updatedAt;
    }

    @Getter
    @Setter
    public static class PortfolioView {
        @NotNull
        private String id;
        @NotNull
        private String name;
    }
}
