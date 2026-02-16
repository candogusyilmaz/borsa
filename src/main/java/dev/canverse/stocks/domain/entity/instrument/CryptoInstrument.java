package dev.canverse.stocks.domain.entity.instrument;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(schema = "instrument", name = "crypto_instruments")
@PrimaryKeyJoinColumn(name = "instrument_id")
public class CryptoInstrument extends Instrument {

    protected CryptoInstrument() {
    }

    public CryptoInstrument(String name, String symbol, Market market) {
        super(name, symbol, InstrumentType.CRYPTOCURRENCY, market);
    }
}
