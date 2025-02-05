package dev.canverse.stocks.rest.member;

import dev.canverse.stocks.service.member.HoldingService;
import dev.canverse.stocks.service.member.model.Balance;
import dev.canverse.stocks.service.member.model.BalanceHistory;
import dev.canverse.stocks.service.member.model.TradeHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/member")
public class MemberController {
    private final HoldingService holdingService;

    @GetMapping("/balance")
    public Balance fetchBalance() {
        return holdingService.fetchBalance();
    }

    @GetMapping("/balance/history/{lastDays}")
    public List<BalanceHistory> fetchBalanceHistory(@PathVariable int lastDays) {
        return holdingService.fetchBalanceHistory(lastDays);
    }

    @GetMapping("/trades/history")
    public TradeHistory fetchTradeHistory() {
        return holdingService.fetchTradeHistory();
    }
}
