package dev.canverse.stocks.rest.member;

import dev.canverse.stocks.service.member.HoldingService;
import dev.canverse.stocks.service.member.model.Balance;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/member")
public class MemberController {
    private final HoldingService holdingService;

    @GetMapping("/balance")
    public Balance fetchBalance() {
        return holdingService.fetchBalance();
    }
}
