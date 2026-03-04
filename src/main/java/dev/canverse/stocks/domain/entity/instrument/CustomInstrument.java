package dev.canverse.stocks.domain.entity.instrument;

import dev.canverse.stocks.domain.entity.account.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(schema = "instrument", name = "custom_instruments")
@PrimaryKeyJoinColumn(name = "instrument_id")
public class CustomInstrument extends Instrument {
    @Setter
    @Column(nullable = false, length = 3)
    private String currencyCode;

    protected CustomInstrument() {
    }

    public CustomInstrument(String name, String symbol, InstrumentType type, String currencyCode, User user) {
        super(name, symbol, type, user);
        this.currencyCode = currencyCode;
    }
}
