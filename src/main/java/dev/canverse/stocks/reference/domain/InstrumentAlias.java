package dev.canverse.stocks.reference.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "instrument_alias", schema = "reference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstrumentAlias {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    private AliasType aliasType;

    private String aliasValue;

    private String aliasNormalized;

    private Instant createdAt;

    public static InstrumentAlias create(
            UUID id, Instrument instrument, AliasType type, String value, String normalized, Instant createdAt) {
        var alias = new InstrumentAlias();
        alias.id = Objects.requireNonNull(id, "id");
        alias.instrument = Objects.requireNonNull(instrument, "instrument");
        alias.aliasType = Objects.requireNonNull(type, "type");
        alias.aliasValue = Objects.requireNonNull(value, "value");
        alias.aliasNormalized = Objects.requireNonNull(normalized, "normalized");
        alias.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return alias;
    }
}
