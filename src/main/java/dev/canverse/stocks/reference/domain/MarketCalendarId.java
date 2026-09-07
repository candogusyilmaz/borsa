package dev.canverse.stocks.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Embeddable
public record MarketCalendarId(@Column(name = "market_id") UUID marketId, @Column(name = "calendar_date") LocalDate calendarDate) implements Serializable {}
