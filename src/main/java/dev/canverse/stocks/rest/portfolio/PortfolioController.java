package dev.canverse.stocks.rest.portfolio;

import dev.canverse.stocks.service.member.HoldingService;
import dev.canverse.stocks.service.member.model.Portfolio;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portfolio")
public class PortfolioController {
    private final HoldingService holdingService;

    @GetMapping
    public Portfolio fetchPortfolio(@RequestParam(defaultValue = "false") boolean includeCommission) {
        return holdingService.fetchPortfolio(includeCommission);
    }
}
