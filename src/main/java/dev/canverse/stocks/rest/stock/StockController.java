package dev.canverse.stocks.rest.stock;

import dev.canverse.stocks.domain.common.SelectItem;
import dev.canverse.stocks.service.AsyncJobService;
import dev.canverse.stocks.service.stock.StockService;
import dev.canverse.stocks.service.stock.model.Stocks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class StockController {
    private final StockService stockService;
    private final AsyncJobService asyncJobService;


    @GetMapping
    public Stocks fetchStocks(String exchange) {
        return stockService.fetchStocks(exchange);
    }

    @GetMapping("/lookup")
    public List<SelectItem> fetchLookupStocks(Optional<String> exchange) {
        return stockService.fetchLookupStocks(exchange);
    }

    @PostMapping("/import")
    public void importStocks() {
        asyncJobService.runImportStocksJobAsync();
    }
}
