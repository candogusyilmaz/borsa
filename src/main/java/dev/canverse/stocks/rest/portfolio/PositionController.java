package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.security.AuthenticationProvider;
import dev.canverse.stocks.service.portfolio.PositionService;
import dev.canverse.stocks.service.portfolio.TradeService;
import dev.canverse.stocks.service.portfolio.model.PositionInfo;
import dev.canverse.stocks.service.portfolio.model.TransactionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/positions")
public class PositionController {
    private final PositionService positionService;
    private final TradeService tradeService;

    @GetMapping
    public List<PositionInfo> fetchPositions() {
        return positionService.fetchPositions(AuthenticationProvider.getUser().getId());
    }

    @GetMapping("/{positionId}/active-trades")
    public List<TransactionInfo> fetchActiveTrades(@PathVariable Long positionId) {
        return tradeService.fetchActiveTrades(AuthenticationProvider.getUser().getId(), positionId);
    }
}
