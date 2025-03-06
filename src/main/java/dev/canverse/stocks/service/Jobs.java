package dev.canverse.stocks.service;

import dev.canverse.stocks.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Jobs {
    private final StockService stockService;

    @Scheduled(cron = "0/30 15-30 10-18 ? * MON-FRI", zone = "GMT+3")
    public void updateBIST() {
        stockService.updateBIST();
    }
}
