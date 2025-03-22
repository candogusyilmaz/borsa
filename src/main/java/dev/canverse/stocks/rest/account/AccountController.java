package dev.canverse.stocks.rest.account;

import dev.canverse.stocks.service.portfolio.HoldingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
public class AccountController {
    private final HoldingService holdingService;

    @PostMapping("/clear-my-data")
    public void clearMyData() {
        holdingService.deleteAllHoldings();
    }
}
