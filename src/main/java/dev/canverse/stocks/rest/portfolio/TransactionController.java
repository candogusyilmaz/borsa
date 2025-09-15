package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.portfolio.TransactionService;
import dev.canverse.stocks.service.portfolio.model.BuyTransactionRequest;
import dev.canverse.stocks.service.portfolio.model.SellTransactionRequest;
import dev.canverse.stocks.service.portfolio.model.TransactionHistory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolios/{portfolioId}/trades")
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping
    public TransactionHistory fetchTrades(@PathVariable long portfolioId) {
        return transactionService.getTransactionHistory(portfolioId);
    }

    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public void buy(@Valid @RequestBody BuyTransactionRequest req, @PathVariable long portfolioId) {
        transactionService.buy(portfolioId, req);
    }

    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void buy(@Valid @RequestBody SellTransactionRequest req, @PathVariable long portfolioId) {
        transactionService.sell(portfolioId, req);
    }

    @PostMapping("/undo/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable long holdingId, @PathVariable long portfolioId) {
        transactionService.undoLatestTransaction(holdingId);
    }
}
