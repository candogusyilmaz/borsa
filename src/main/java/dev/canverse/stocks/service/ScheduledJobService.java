package dev.canverse.stocks.service;

import dev.canverse.stocks.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobService {
    private final StockService stockService;
    private final AsyncJobService asyncJobService;

    @Scheduled(cron = "0/30 15-30 7-15 ? * MON-FRI")
    public void updateBist() {
        stockService.updateBist();
    }

    @Scheduled(cron = "0 0 7,17 ? * MON-FRI")
    public void runImportStocksJob() {
        asyncJobService.runImportStocksJobAsync();
    }
}
