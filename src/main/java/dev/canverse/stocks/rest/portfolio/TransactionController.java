package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import dev.canverse.stocks.service.portfolio.TradeService;
import dev.canverse.stocks.service.portfolio.TransactionImportService;
import dev.canverse.stocks.service.portfolio.model.BulkTransactionRequest;
import dev.canverse.stocks.service.portfolio.model.TransactionHistory;
import dev.canverse.stocks.service.portfolio.model.TransactionRequest;
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
public class TransactionController {
    private final TradeService tradeService;
    private final TransactionImportService transactionImportService;

    @GetMapping
    public TransactionHistory fetchTrades(@PathVariable long portfolioId) {
        return tradeService.getTransactionHistory(portfolioId);
    }

    @PostMapping("/buy")
    public void buy(@Valid @RequestBody TransactionRequest req, @PathVariable long portfolioId) {
        tradeService.buy(portfolioId, req);
    }

    @PostMapping("/sell")
    public void sell(@Valid @RequestBody TransactionRequest req, @PathVariable long portfolioId) {
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
        tradeService.undoLatestTransaction(holdingId);
    }

    @PostMapping("/import")
    public List<BulkTransactionRequest> importTransactions(@RequestParam("file") MultipartFile file) {
        var transactionImportPreviews = transactionImportService.importTransactions(file);
        return transactionImportService.parseImportedTransactions(transactionImportPreviews);
    }
}
