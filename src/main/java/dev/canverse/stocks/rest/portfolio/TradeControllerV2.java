package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.TradeService;
import dev.canverse.stocks.service.portfolio.model.TradeInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TradeControllerV2 {
    private final TradeService tradeService;

    @GetMapping
    public List<TradeInfo> fetchTrades() {
        return tradeService.fetchTrades(AuthenticationProvider.getUser().getId());
    }
}
