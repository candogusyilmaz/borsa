package dev.canverse.stocks.dataloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.canverse.stocks.domain.entity.Stock;
import dev.canverse.stocks.repository.CountryRepository;
import dev.canverse.stocks.repository.ExchangeRepository;
import dev.canverse.stocks.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Order(1000)
@Component
@RequiredArgsConstructor
public class StocksDataLoader implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(StocksDataLoader.class);

    private final ObjectMapper mapper;

    private final CountryRepository countryRepository;

    private final StockRepository stockRepository;
    private final ExchangeRepository exchangeRepository;

    @Override
    public void run(String... args) {
        if (stockRepository.count() != 0) return;

        var resource = new ClassPathResource("/data/symbols.json");

        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            var stocks = mapper.readValue(reader, StockJson[].class);
            var turkiye = countryRepository.findByIsoCode("TR");

            var stockEntities = new ArrayList<Stock>();
            var exchange = exchangeRepository.findByCode("BIST");

            for (var stock : stocks) {
                var entity = new Stock(exchange, stock.description(), stock.symbolCode(), stock.isinCode(), turkiye);
                stockEntities.add(entity);
            }

            stockRepository.saveAll(stockEntities);
        } catch (IOException e) {
            log.error("Failed to load stocks data", e);
        }
    }

    record StockJson(Long symbolId, Long modifiedAt, String symbolCode, Boolean deleted, String description,
                     Long sectorId, Long exchangeId, String marketCode, Long fraction, Long tradeFraction,
                     String symbolType, String stockSymbolCode, Long tradeableStatus, Boolean isProxy, String isinCode,
                     Boolean multiSession, Long delay, String exchangeCode, String actionType, List<String> indexes,
                     String sectorCode, Long marketId, Long tradeStatus, String swapType, String marketMaker) {
    }
}

