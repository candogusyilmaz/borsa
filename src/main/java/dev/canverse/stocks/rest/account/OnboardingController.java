package dev.canverse.stocks.rest.account;

import dev.canverse.stocks.service.account.UserService;
import dev.canverse.stocks.service.account.model.OnboardingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/onboarding")
public class OnboardingController {
    private final UserService userService;

    @GetMapping("/status")
    public boolean getOnboardingStatus() {
        return userService.getOnboardingStatus();
    }

    @PostMapping("/complete")
    public void completeOnboarding(@Valid @RequestBody OnboardingRequest request) {
        userService.completeOnboarding(request);
    }
}
