package dev.canverse.stocks.service;

import dev.canverse.stocks.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Jobs {
    private final StockService stockService;

    @Scheduled(cron = "0/30 * 7-15 ? * MON-FRI")
    public void updateBIST() {
        stockService.updateBIST();
    }
}
