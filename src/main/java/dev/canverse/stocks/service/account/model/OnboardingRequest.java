package dev.canverse.stocks.service.account.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OnboardingRequest(@NotNull
                                @NotEmpty
                                String dashboardName,
                                @NotNull
                                @Length(min = 3, max = 3)
                                String currencyCode,
                                @NotNull
                                PortfolioSetup portfolio) {

    public record PortfolioSetup(
            @NotNull
            @NotEmpty
            String portfolioName,
            @NotNull
            @NotEmpty
            String color,
            List<TradeSetup> trades
    ) {

        public record TradeSetup(
                @NotNull
                @Positive
                Long instrumentId,
                @Positive
                BigDecimal quantity,
                @Positive
                BigDecimal price,
                Instant actionDate
        ) {
        }
    }
}
