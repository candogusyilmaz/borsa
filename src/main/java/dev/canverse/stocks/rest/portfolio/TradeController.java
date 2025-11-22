package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import dev.canverse.stocks.service.portfolio.TradeImportService;
import dev.canverse.stocks.service.portfolio.TradeService;
import dev.canverse.stocks.service.portfolio.model.BulkTransactionRequest;
import dev.canverse.stocks.service.portfolio.model.TradeHistory;
import dev.canverse.stocks.service.portfolio.model.TradeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolios/{portfolioId}/trades")
public class TradeController {
    private final TradeService tradeService;
    private final TradeImportService tradeImportService;

    @GetMapping
    public TradeHistory fetchTrades(@PathVariable long portfolioId) {
        return tradeService.fetchTradeHistory(portfolioId);
    }

    @PostMapping("/buy")
    public void buy(@Valid @RequestBody TradeRequest req, @PathVariable long portfolioId) {
        tradeService.buy(portfolioId, req);
    }

    @PostMapping("/sell")
    public void sell(@Valid @RequestBody TradeRequest req, @PathVariable long portfolioId) {
        tradeService.sell(portfolioId, req);
    }

    @PostMapping("/bulk")
    @Transactional
    public void bulk(@Valid @RequestBody List<BulkTransactionRequest> reqs, @PathVariable long portfolioId) {
        reqs.sort(Comparator.comparing(BulkTransactionRequest::actionDate));

        for (var req : reqs) {
            if (req.type() == Transaction.Type.BUY) {
                tradeService.buy(portfolioId, req.toTransactionRequest());
            } else {
                tradeService.sell(portfolioId, req.toTransactionRequest());
            }
        }
    }

    @PostMapping("/undo/{holdingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable long holdingId, @PathVariable long portfolioId) {
        tradeService.undoLatestTrade(holdingId);
    }

    @PostMapping("/import")
    public List<BulkTransactionRequest> importTransactions(@RequestParam("file") MultipartFile file) {
        var transactionImportPreviews = tradeImportService.importTransactions(file);
        return tradeImportService.parseImportedTransactions(transactionImportPreviews);
    }
}
