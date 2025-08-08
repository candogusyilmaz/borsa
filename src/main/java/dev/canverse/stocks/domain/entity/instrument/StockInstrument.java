package dev.canverse.stocks.domain.entity.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(schema = "instrument", name = "stock_instruments")
@PrimaryKeyJoinColumn(name = "instrument_id")
public class StockInstrument extends Instrument {
    @Column(length = 12)
    private String isin;

    protected StockInstrument() {
    }

    public StockInstrument(String name, String symbol, Market market, String denominationCurrency, String isin) {
        super(name, symbol, InstrumentType.STOCK, market, denominationCurrency);
        this.isin = isin;
    }
}
