package dev.canverse.stocks.domain.entity.instrument;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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
    @OneToMany(mappedBy = "instrument", cascade = CascadeType.ALL)
    private Set<InstrumentSnapshot> snapshots = new HashSet<>();

    protected Instrument() {
    }

    public enum InstrumentType {
        STOCK, CRYPTOCURRENCY, CURRENCY_PAIR, COMMODITY, INDEX
    }

    public Instrument(String name, String symbol, InstrumentType type, Market market) {
        this.name = name;
        this.symbol = symbol;
        this.type = type;
        this.market = market;
        this.isActive = true;
    }
}
