package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.TransactionService;
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
    private final TransactionService transactionService;

    @GetMapping
    public List<TransactionInfo> fetchAllTransactions() {
        return transactionService.findTransactions(AuthenticationProvider.getUser().getId());
    }
}
