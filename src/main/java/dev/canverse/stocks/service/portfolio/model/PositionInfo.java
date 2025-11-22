package dev.canverse.stocks.service.portfolio.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PositionInfo {
    private String id;
    private InstrumentView instrument;
    private PortfolioView portfolio;
    private BigDecimal quantity;
    private BigDecimal total;
    private BigDecimal avgCost;

    @Getter
    @Setter
    public static class InstrumentView {
        private String id;
        private String name;
        private String symbol;
        private String currency;
        private BigDecimal last;
        private BigDecimal dailyChange;
    }

    @Getter
    @Setter
    public static class PortfolioView {
        private String id;
        private String name;
    }
}
