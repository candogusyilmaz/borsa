package dev.canverse.stocks.service.stock;


import dev.canverse.stocks.domain.entity.Country;
import dev.canverse.stocks.domain.entity.Exchange;
import dev.canverse.stocks.domain.entity.Stock;
import dev.canverse.stocks.repository.CountryRepository;
import dev.canverse.stocks.repository.ExchangeRepository;
import dev.canverse.stocks.repository.StockRepository;
import dev.canverse.stocks.service.stock.model.BistImportStockCsvRecord;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class BistImportStockProcessor implements ItemProcessor<BistImportStockCsvRecord, Stock> {
    private final Exchange BIST;
    private final Country TURKEY;

    private final StockRepository stockRepository;

    public BistImportStockProcessor(ExchangeRepository exchangeRepository, CountryRepository countryRepository, StockRepository stockRepository) {
        this.stockRepository = stockRepository;
        this.BIST = exchangeRepository.findByCode("BIST");
        this.TURKEY = countryRepository.findByIsoCode("TR");
    }

    @Override
    public Stock process(BistImportStockCsvRecord record) {
        if (!record.isEquity()) {
            return null;
        }

        var stock = stockRepository.findBySymbol(record.getIslemKodu(), BIST.getId());

        if (stock.isPresent())
            return null;

        return new Stock(
                BIST,
                record.bultenAdi(),
                record.getIslemKodu(),
                TURKEY,
                record.enstrumanGrubu()
        );
    }
}