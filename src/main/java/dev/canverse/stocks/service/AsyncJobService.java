package dev.canverse.stocks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncJobService {
    private final JobLauncher jobLauncher;
    private final Job importStocksJob;

    @Async
    public void runImportStocksJobAsync() {
        try {
            var jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            log.info("Starting async import stocks job");
            jobLauncher.run(importStocksJob, jobParameters);
            log.info("Completed async import stocks job");
        } catch (Exception e) {
            log.error("Error running async import stocks job", e);
        }
    }
}
