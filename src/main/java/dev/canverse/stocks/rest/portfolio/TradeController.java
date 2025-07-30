package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.portfolio.TradeService;
import dev.canverse.stocks.service.portfolio.model.BuyTradeRequest;
import dev.canverse.stocks.service.portfolio.model.SellTradeRequest;
import dev.canverse.stocks.service.portfolio.model.TradeHistory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolios/{portfolioId}/trades")
public class TradeController {
    private final TradeService tradeService;

    @GetMapping
    public TradeHistory fetchTrades(@PathVariable long portfolioId) {
        return tradeService.fetchTrades(portfolioId);
    }

    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public void buy(@Valid @RequestBody BuyTradeRequest req, @PathVariable long portfolioId) {
        tradeService.buy(portfolioId, req);
    }

    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void buy(@Valid @RequestBody SellTradeRequest req, @PathVariable long portfolioId) {
        tradeService.sell(portfolioId, req);
    }

    @PostMapping("/undo/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable long holdingId, @PathVariable long portfolioId) {
        tradeService.undoLatestTrade(portfolioId, holdingId);
    }
}
