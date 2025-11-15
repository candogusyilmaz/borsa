package dev.canverse.stocks.service.portfolio.model;

import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class TransactionInfo {
    private String id;
    private PositionView position;
    private Transaction.Type type;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal newQuantity;
    private BigDecimal newTotal;
    private BigDecimal profit;
    private Instant actionDate;

    private List<String> tags;
    private String notes;

    @Getter
    @Setter
    public static class PositionView {
        private String positionId;
        private String instrumentSymbol;
        private String currencyCode;
        private PortfolioView portfolio;

        @Getter
        @Setter
        public static class PortfolioView {
            private String id;
            private String name;
        }
    }
}
