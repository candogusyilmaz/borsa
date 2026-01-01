package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.TradeService;
import dev.canverse.stocks.service.portfolio.model.FetchTradesQuery;
import dev.canverse.stocks.service.portfolio.model.TradeInfo;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/trades")
public class TradeControllerV2 {
    private final TradeService tradeService;

    @GetMapping
    public List<TradeInfo> fetchTrades(@ParameterObject FetchTradesQuery query) {
        return tradeService.fetchTrades(AuthenticationProvider.getUser().getId(), query);
    }
}
