package dev.canverse.stocks.rest.stock;

import dev.canverse.stocks.domain.common.SelectItem;
import dev.canverse.stocks.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class StockController {
    private final StockService stockService;

    @GetMapping("/lookup")
    public List<SelectItem> fetchLookupStocks(Optional<String> exchange) {
        return stockService.fetchLookupStocks(exchange);
    }
}
