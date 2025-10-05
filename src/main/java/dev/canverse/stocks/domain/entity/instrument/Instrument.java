package dev.canverse.stocks.domain.entity.instrument;

import dev.canverse.stocks.domain.Calculator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(schema = "instrument", name = "instruments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "market_id"}),
        indexes = {@Index(name = "idx_instruments_symbol", columnList = "symbol"),
                @Index(name = "idx_instruments_type", columnList = "type"),
                @Index(name = "idx_instruments_market", columnList = "market_id")})
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Instrument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, columnDefinition = "instrument_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private InstrumentType type;

    @Column(nullable = false, length = 3)
    private String denominationCurrency;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Market market;

    @Setter
    @Column(nullable = false)
    private boolean isActive;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Setter
    @OneToOne(mappedBy = "instrument", cascade = CascadeType.ALL)
    private InstrumentSnapshot snapshot;

    protected Instrument() {
    }

    public enum InstrumentType {
        STOCK, CRYPTOCURRENCY, CURRENCY_PAIR, COMMODITY, INDEX
    }

    public Instrument(String name, String symbol, InstrumentType type, Market market, String denominationCurrency) {
        this.name = name;
        this.symbol = symbol;
        this.type = type;
        this.market = market;
        this.denominationCurrency = denominationCurrency;
        this.isActive = true;
        this.snapshot = new InstrumentSnapshot(this);
    }

    public void updateSnapshot(BigDecimal last, BigDecimal previousClose) {
        var dailyChange = last.subtract(previousClose);
        var dailyChangePercent = Calculator.divide(last.subtract(previousClose).multiply(BigDecimal.valueOf(100)), previousClose);

        this.updateSnapshot(last, previousClose, dailyChange, dailyChangePercent);
    }

    public void updateSnapshot(BigDecimal last, BigDecimal previousClose, BigDecimal dailyChange, BigDecimal dailyChangePercent) {
        var snapshot = this.getSnapshot();

        if (snapshot == null) {
            snapshot = new InstrumentSnapshot(this);
        }

        snapshot.setLast(last);
        snapshot.setDailyChange(dailyChange);
        snapshot.setDailyChangePercent(dailyChangePercent);
        snapshot.setPreviousClose(previousClose);
        snapshot.setUpdatedAt(Instant.now());
    }
}
