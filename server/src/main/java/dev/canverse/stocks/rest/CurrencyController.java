package dev.canverse.stocks.rest;

import dev.canverse.stocks.domain.common.SelectItem;
import dev.canverse.stocks.service.common.CurrencyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/currencies")
public class CurrencyController {
    private final CurrencyService currencyService;

    @GetMapping
    public List<SelectItem> getAllCurrencies() {
        return currencyService.getAllCurrencies();
    }
}
