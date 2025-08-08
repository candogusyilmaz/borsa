package dev.canverse.stocks.domain.entity.instrument;

import dev.canverse.stocks.domain.entity.Country;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Entity
@Table(schema = "instrument", name = "markets")
public class Market implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotEmpty
    @Column(nullable = false)
    private String name;

    @NotEmpty
    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketType type;

    @ManyToOne(fetch = FetchType.LAZY)
    private Country country;

    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public enum MarketType {
        STOCK_EXCHANGE,     // BIST, NYSE, NASDAQ
        CRYPTOCURRENCY,     // Binance, Coinbase
        FOREX,              // Forex market
        COMMODITY,          // Gold, Silver, Oil
        INDEX,              // S&P 500, BIST 100
    }

    protected Market() {
    }

    public Market(String name, String code, MarketType type, String timezone) {
        this.name = name;
        this.code = code;
        this.type = type;
        this.timezone = timezone;
    }

    public Market(String name, String code, MarketType type, Country country, String timezone) {
        this.name = name;
        this.code = code;
        this.type = type;
        this.country = country;
        this.timezone = timezone;
    }
}
