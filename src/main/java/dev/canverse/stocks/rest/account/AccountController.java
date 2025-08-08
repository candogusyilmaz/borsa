package dev.canverse.stocks.rest.account;

import dev.canverse.stocks.service.portfolio.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
public class AccountController {
    private final PositionService positionService;

    @PostMapping("/clear-my-data")
    public void clearMyData() {
        positionService.deleteAllPositions();
    }
}
