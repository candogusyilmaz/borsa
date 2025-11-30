package dev.canverse.stocks.service.portfolio.model;

import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class TradeInfo {
    @NotNull
    private String id;
    @NotNull
    private PositionView position;
    @NotNull
    private Transaction.Type type;
    @NotNull
    private BigDecimal quantity;
    @NotNull
    private BigDecimal price;
    @NotNull
    private BigDecimal newQuantity;
    @NotNull
    private BigDecimal newTotal;
    private BigDecimal profit;
    @NotNull
    private Instant actionDate;

    private List<String> tags;
    private String notes;

    @Getter
    @Setter
    public static class PositionView {
        @NotNull
        private String positionId;
        @NotNull
        private String instrumentSymbol;
        @NotNull
        private String instrumentName;
        @NotNull
        private String currencyCode;
        @NotNull
        private PortfolioView portfolio;

        @Getter
        @Setter
        public static class PortfolioView {
            @NotNull
            private String id;
            @NotNull
            private String name;
        }
    }
}
