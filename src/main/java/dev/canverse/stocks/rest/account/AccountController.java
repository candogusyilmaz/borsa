package dev.canverse.stocks.rest.account;

import dev.canverse.stocks.service.account.UserService;
import dev.canverse.stocks.service.account.model.UserView;
import dev.canverse.stocks.service.portfolio.PositionService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account")
public class AccountController {
    private final UserService userService;
    private final PositionService positionService;

    @GetMapping("/me")
    public UserView getMyAccount() {
        return userService.getMe();
    }

    @PostMapping("/clear-my-data")
    public void clearMyData() {
        positionService.deleteAllPositions();
    }
}
