package dev.canverse.stocks.service.stock;


import dev.canverse.stocks.domain.entity.instrument.Market;
import dev.canverse.stocks.domain.entity.instrument.StockInstrument;
import dev.canverse.stocks.repository.MarketRepository;
import dev.canverse.stocks.repository.StockInstrumentRepository;
import dev.canverse.stocks.service.stock.model.BistImportStockCsvRecord;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class BistImportStockProcessor implements ItemProcessor<BistImportStockCsvRecord, StockInstrument> {
    private final Market BIST;

    private final StockInstrumentRepository stockInstrumentRepository;

    public BistImportStockProcessor(MarketRepository marketRepository, StockInstrumentRepository stockInstrumentRepository) {
        this.stockInstrumentRepository = stockInstrumentRepository;
        this.BIST = marketRepository.findByCode("BIST");
    }

    @Override
    public StockInstrument process(BistImportStockCsvRecord record) {
        if (!record.isEquity()) {
            return null;
        }

        var stock = stockInstrumentRepository.findBySymbol(record.getIslemKodu(), BIST.getId());

        if (stock.isPresent()) {
            stock.get().updateSnapshot(
                    record.kapanisFiyati(),
                    record.oncekiKapanisFiyati()
            );

            stockInstrumentRepository.save(stock.get());
            return null;
        }

        var newStock = new StockInstrument(
                record.bultenAdi(),
                record.getIslemKodu(),
                BIST,
                "TRY"
        );

        newStock.updateSnapshot(
                record.kapanisFiyati(),
                record.oncekiKapanisFiyati()
        );

        return newStock;
    }
}