package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.portfolio.PortfolioService;
import dev.canverse.stocks.service.portfolio.PositionService;
import dev.canverse.stocks.service.portfolio.model.BasicPortfolioView;
import dev.canverse.stocks.service.portfolio.model.CreatePortfolioRequest;
import dev.canverse.stocks.service.portfolio.model.PortfolioInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolios")
public class PortfolioController {
    private final PositionService positionService;
    private final PortfolioService portfolioService;

    @GetMapping
    public List<BasicPortfolioView> fetchPortfolio() {
        return portfolioService.fetchBasicPortfolioViews();
    }

    @GetMapping("/{portfolioId}")
    public PortfolioInfo fetchPortfolio(@PathVariable long portfolioId) {
        return positionService.fetchPortfolio(portfolioId);
    }

    @PostMapping
    public void createPortfolio(@RequestBody CreatePortfolioRequest req) {
        portfolioService.createPortfolio(req.name().trim().replaceAll("\\s+", " "));
    }
}
