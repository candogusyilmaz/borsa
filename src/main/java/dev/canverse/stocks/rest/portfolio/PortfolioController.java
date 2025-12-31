package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.portfolio.PortfolioService;
import dev.canverse.stocks.service.portfolio.model.BasicPortfolioView;
import dev.canverse.stocks.service.portfolio.model.CreatePortfolioRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolios")
public class PortfolioController {
    private final PortfolioService portfolioService;

    @GetMapping
    public List<BasicPortfolioView> fetchPortfolio() {
        return portfolioService.fetchBasicPortfolioViews();
    }

    @PostMapping
    public void createPortfolio(@RequestBody CreatePortfolioRequest req) {
        portfolioService.createPortfolio(req.name().trim().replaceAll("\\s+", " "));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{portfolioId}/archive")
    public void archivePortfolio(@PathVariable long portfolioId) {
        portfolioService.archivePortfolio(portfolioId);
    }
}
