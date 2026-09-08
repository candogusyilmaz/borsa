package dev.canverse.stocks.reference.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_calendar", schema = "reference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketCalendar {

    @EmbeddedId
    private MarketCalendarId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("marketId")
    @JoinColumn(name = "market_id")
    private Market market;

    @Enumerated(EnumType.STRING)
    private MarketSessionStatus sessionStatus;

    private LocalTime opensAt;

    private LocalTime closesAt;

    private String sourceKind;

    private Instant createdAt;
}
