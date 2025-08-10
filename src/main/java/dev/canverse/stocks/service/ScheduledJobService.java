package dev.canverse.stocks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledJobService {
    private final AsyncJobService asyncJobService;

    @Scheduled(cron = "0 0 7,16 ? * MON-FRI")
    public void runImportStocksJob() {
        asyncJobService.runImportStocksJobAsync();
    }
}
