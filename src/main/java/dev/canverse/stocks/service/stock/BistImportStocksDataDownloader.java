package dev.canverse.stocks.service.stock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class BistImportStocksDataDownloader implements Tasklet {

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        var date = LocalDate.now();
        var url = buildUrl(date);

        var downloadDir = Paths.get("downloaded");
        Files.createDirectories(downloadDir);

        downloadAndExtractZip(url, downloadDir);

        return RepeatStatus.FINISHED;
    }

    private static String buildUrl(LocalDate date) {
        return String.format("https://www.borsaistanbul.com/data/thb/%d/%02d/thb%d%02d%02d1.zip",
                date.getYear(),
                date.getMonthValue(),
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth());
    }

    private static void downloadAndExtractZip(String urlStr, Path downloadDir) throws IOException {
        var url = new URL(urlStr);
        try (var in = new BufferedInputStream(url.openStream()); var zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".csv")) {
                    var outputPath = downloadDir.resolve("stocks.csv");

                    if (Files.exists(outputPath)) {
                        log.warn("File already exists, deleting: {}", outputPath);
                        Files.delete(outputPath);
                    }

                    Files.copy(zis, outputPath);
                    log.info("CSV file extracted to: {}", outputPath);
                }
            }
        }
    }
}