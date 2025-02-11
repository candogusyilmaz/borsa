package dev.canverse.stocks.rest.stock;

import dev.canverse.stocks.service.stock.TradeService;
import dev.canverse.stocks.service.stock.model.BuyTradeRequest;
import dev.canverse.stocks.service.stock.model.SellTradeRequest;
import dev.canverse.stocks.service.stock.model.TradesHeatMap;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trades")
public class TradeController {
    private final TradeService tradeService;

    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public void buy(@Valid @RequestBody BuyTradeRequest req) {
        tradeService.buy(req);
    }

    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void buy(@Valid @RequestBody SellTradeRequest req) {
        tradeService.sell(req);
    }

    @GetMapping("/heat-map")
    public List<TradesHeatMap> getTradesHeatMap() {
        return tradeService.getTradesHeatMap();
    }
}
