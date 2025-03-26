package dev.canverse.stocks.service;

import dev.canverse.stocks.service.portfolio.HoldingService;
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
    private final HoldingService holdingService;
    private final AsyncJobService asyncJobService;

    @Scheduled(cron = "0/30 15-30 10-18 ? * MON-FRI", zone = "GMT+3")
    public void updateBist() {
        stockService.updateBist();
    }

    //@Scheduled(cron = "0 30 18 ? * MON-FRI", zone = "GMT+3")
    public void generateDailyHoldingSnapshots() {
        holdingService.generateDailyHoldingSnapshots();
    }

    //@Scheduled(cron = "0 0 9,20 * * *", zone = "GMT+3")
    public void processStockSplits() {
        stockService.processStockSplits();
    }

    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = "GMT+3")
    public void runImportStocksJob() {
        asyncJobService.runImportStocksJobAsync();
    }
}
