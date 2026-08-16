package dev.canverse.stocks.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketCurrencyId implements Serializable {

    @Column(name = "market_id")
    private UUID marketId;

    @Column(name = "currency_code")
    private String currencyCode;

    public MarketCurrencyId(UUID marketId, String currencyCode) {
        this.marketId = Objects.requireNonNull(marketId, "marketId");
        this.currencyCode = Objects.requireNonNull(currencyCode, "currencyCode");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MarketCurrencyId that)) {
            return false;
        }
        return Objects.equals(marketId, that.marketId) && Objects.equals(currencyCode, that.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketId, currencyCode);
    }
}
