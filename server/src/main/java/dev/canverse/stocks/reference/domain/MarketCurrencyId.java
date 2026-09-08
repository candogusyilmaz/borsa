package dev.canverse.stocks.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record MarketCurrencyId(@Column(name = "market_id") UUID marketId, @Column(name = "currency_code") String currencyCode) implements Serializable {}
