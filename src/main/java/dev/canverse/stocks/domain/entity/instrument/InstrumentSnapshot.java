package dev.canverse.stocks.domain.entity.instrument;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(schema = "instrument", name = "instrument_snapshots")
public class InstrumentSnapshot implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Instrument instrument;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal last;

    @Column(precision = 38, scale = 18)
    private BigDecimal previousClose;

    @Column(precision = 38, scale = 18)
    private BigDecimal dailyChange;

    @Column(precision = 38, scale = 18)
    private BigDecimal dailyChangePercent;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected InstrumentSnapshot() {
    }

    protected InstrumentSnapshot(Instrument instrument) {
        this.instrument = instrument;
    }
}
