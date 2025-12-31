package dev.canverse.stocks.domain.entity.instrument;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "market_currencies", schema = "instrument")
public class MarketCurrency {
    @EmbeddedId
    private MarketCurrencyId id;

    @MapsId("marketId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;
}