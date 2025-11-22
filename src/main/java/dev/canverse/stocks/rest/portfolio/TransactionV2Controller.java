package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.TradeService;
import dev.canverse.stocks.service.portfolio.model.TransactionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TransactionV2Controller {
    private final TradeService tradeService;

    @GetMapping
    public List<TransactionInfo> fetchAllTransactions() {
        return tradeService.fetchTransactions(AuthenticationProvider.getUser().getId());
    }
}
