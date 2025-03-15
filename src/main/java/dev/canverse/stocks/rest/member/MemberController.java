package dev.canverse.stocks.rest.member;

import dev.canverse.stocks.service.member.HoldingService;
import dev.canverse.stocks.service.member.model.BalanceHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/member")
public class MemberController {
    private final HoldingService holdingService;

    @GetMapping("/balance/history/{lastDays}")
    public List<BalanceHistory> fetchBalanceHistory(@PathVariable int lastDays) {
        return holdingService.fetchBalanceHistory(lastDays);
    }

    @PostMapping("/clear-my-data")
    public void clearMyData() {
        holdingService.deleteAllHoldings();
    }
}
