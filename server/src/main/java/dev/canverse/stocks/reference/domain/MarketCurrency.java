package dev.canverse.stocks.reference.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_currency", schema = "reference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketCurrency {

    @EmbeddedId
    private MarketCurrencyId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("marketId")
    @JoinColumn(name = "market_id")
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("currencyCode")
    @JoinColumn(name = "currency_code")
    private Currency currency;

    private boolean primaryQuote;
}
