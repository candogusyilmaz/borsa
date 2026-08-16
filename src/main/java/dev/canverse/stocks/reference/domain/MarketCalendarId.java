package dev.canverse.stocks.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketCalendarId implements Serializable {

    @Column(name = "market_id")
    private UUID marketId;

    @Column(name = "calendar_date")
    private LocalDate calendarDate;

    public MarketCalendarId(UUID marketId, LocalDate calendarDate) {
        this.marketId = Objects.requireNonNull(marketId, "marketId");
        this.calendarDate = Objects.requireNonNull(calendarDate, "calendarDate");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MarketCalendarId that)) {
            return false;
        }
        return Objects.equals(marketId, that.marketId) && Objects.equals(calendarDate, that.calendarDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marketId, calendarDate);
    }
}
