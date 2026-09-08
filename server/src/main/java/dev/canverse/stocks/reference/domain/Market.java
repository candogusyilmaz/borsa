package dev.canverse.stocks.reference.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market", schema = "reference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Market {

    @Id
    private UUID id;

    private String code;

    private String codeNormalized;

    private String name;

    private String marketType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_code")
    private Country country;

    private String timeZone;

    private boolean active;

    private String sourceKind;

    private Instant createdAt;

    private Instant updatedAt;
}
