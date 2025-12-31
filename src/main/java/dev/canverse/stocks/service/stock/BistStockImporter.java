package dev.canverse.stocks.service.stock;

import dev.canverse.stocks.domain.entity.instrument.Market;
import dev.canverse.stocks.domain.entity.instrument.StockInstrument;
import dev.canverse.stocks.repository.MarketRepository;
import dev.canverse.stocks.repository.StockInstrumentRepository;
import dev.canverse.stocks.service.stock.model.BistImportStockCsvRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class BistStockImporter {

    private final Market BIST;

    private final StockInstrumentRepository stockInstrumentRepository;

    public BistStockImporter(MarketRepository marketRepository, StockInstrumentRepository stockInstrumentRepository) {
        this.stockInstrumentRepository = stockInstrumentRepository;
        this.BIST = marketRepository.findByCode("BIST");
        if (this.BIST == null) {
            log.error("Market with code BIST not found. Importer will be effectively disabled.");
        }
    }

    @Async
    @Scheduled(cron = "0 0 7,16 ? * MON-FRI")
    protected void start() {
        if (BIST == null) {
            return; // Safety if market not available
        }
        download();
        Path csvPath = Paths.get("downloaded", "stocks.csv");
        if (!Files.exists(csvPath)) {
            log.warn("CSV file not found at {} after download step. Skipping processing.", csvPath.toAbsolutePath());
            return;
        }

        List<BistImportStockCsvRecord> records;
        try {
            records = readCsv(csvPath);
        } catch (IOException e) {
            log.error("Failed reading CSV file {}", csvPath.toAbsolutePath(), e);
            return;
        }

        int processed = 0;
        for (BistImportStockCsvRecord record : records) {
            try {
                if (processStock(record)) {
                    processed++;
                }
            } catch (Exception ex) {
                log.error("Error processing record {}", record, ex);
            }
        }
        log.info("Finished processing {} of {} parsed records from {}", processed, records.size(), csvPath.toAbsolutePath());
    }

    private void download() {
        LocalDateTime date = LocalDateTime.now();

        if (date.getDayOfWeek().equals(DayOfWeek.SATURDAY) || date.getDayOfWeek().equals(DayOfWeek.SUNDAY)) {
            return;
        }

        if (date.getHour() <= 12) {
            if (date.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
                date = date.minusDays(3);
            } else {
                date = date.minusDays(1);
            }
        }

        var url = buildUrl(date.toLocalDate());
        log.info("Tasklet execution started. Generated URL for download: {}", url);

        var downloadDir = Paths.get("downloaded"); // Relative to WORKDIR

        try {
            Files.createDirectories(downloadDir);
            log.info("Successfully created/ensured directory: {}", downloadDir.toAbsolutePath());
            downloadAndExtractZip(url, downloadDir);
        } catch (IOException e_io_create_dir) {
            log.error("Failed to create download directory: {}", downloadDir.toAbsolutePath(), e_io_create_dir);
        } catch (Exception e) {
            log.error("Exception during execute method, potentially from downloadAndExtractZip.", e);
        }

        log.info("Tasklet execution finished.");
    }

    private static String buildUrl(LocalDate date) {
        return String.format("https://www.borsaistanbul.com/data/thb/%d/%02d/thb%d%02d%02d1.zip",
                date.getYear(),
                date.getMonthValue(),
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth());
    }

    private static void downloadAndExtractZip(String urlStr, Path downloadDir) {
        log.info("Attempting to download and extract from URL: {}", urlStr);
        log.info("Target download directory (absolute): {}", downloadDir.toAbsolutePath());

        try {
            var url = URI.create(urlStr).toURL();
            try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                 ZipInputStream zis = new ZipInputStream(in)) {
                log.info("Successfully opened stream to URL: {}", urlStr);
                boolean csvProcessed = processZipEntries(zis, downloadDir);
                if (!csvProcessed) {
                    log.warn("No .csv file was found or processed in the zip archive from URL: {}", urlStr);
                }
            }
        } catch (Exception e) {
            logDownloadError(urlStr, downloadDir, e);
        }
    }

    // Process entries inside the ZIP and extract the first/only CSV to stocks.csv
    private static boolean processZipEntries(ZipInputStream zis, Path downloadDir) throws IOException {
        ZipEntry entry;
        boolean csvFileProcessed = false;
        while ((entry = zis.getNextEntry()) != null) {
            String name = entry.getName();
            log.debug("Processing ZIP entry: {}", name);
            if (!entry.isDirectory() && name.toLowerCase().endsWith(".csv")) {
                Path outputPath = downloadDir.resolve("stocks.csv");
                log.info("Extracting CSV entry: {} -> {}", name, outputPath.toAbsolutePath());

                if (Files.exists(outputPath)) {
                    try {
                        Files.delete(outputPath);
                        log.debug("Deleted existing file: {}", outputPath);
                    } catch (IOException deleteEx) {
                        log.error("Failed deleting existing file {}. Skipping this entry.", outputPath, deleteEx);
                        zis.closeEntry();
                        continue; // Attempt next entry if any
                    }
                }

                long bytesCopied = Files.copy(zis, outputPath);
                log.info("Copied {} bytes from {}", bytesCopied, name);
                if (bytesCopied == 0 && entry.getSize() > 0) {
                    log.warn("Copied 0 bytes for entry {} but entry size reported {}", name, entry.getSize());
                }
                csvFileProcessed = true; // even if 0 bytes, we attempted
            }
            zis.closeEntry();
            if (csvFileProcessed) { // We only need the first CSV
                break;
            }
        }
        return csvFileProcessed;
    }

    // Centralized error classification & logging
    private static void logDownloadError(String urlStr, Path downloadDir, Exception e) {
        String base = String.format("Error downloading/extracting ZIP from %s (target dir: %s)", urlStr, downloadDir.toAbsolutePath());
        switch (e) {
            case java.net.UnknownHostException unknownHostException ->
                    log.error(base + " - Unknown host. Check DNS resolution and network connectivity.", e);
            case java.net.ConnectException connectException ->
                    log.error(base + " - Connection refused. Remote server might be down or blocked by firewall.", e);
            case javax.net.ssl.SSLHandshakeException sslHandshakeException ->
                    log.error(base + " - SSL Handshake failed. Possible missing CA certificates or protocol mismatch.", e);
            case java.io.FileNotFoundException fileNotFoundException ->
                    log.error(base + " - File not found (404). URL may not yet be available.", e);
            case java.net.MalformedURLException malformedURLException ->
                    log.error(base + " - Malformed URL generated.", e);
            case IOException ioException -> log.error(base + " - I/O problem occurred.", e);
            case null, default -> log.error(base + " - Unexpected error.", e);
        }
    }

    private boolean processStock(BistImportStockCsvRecord record) {
        if (!record.isEquity() && !record.isETF()) {
            return false; // Only process if Equity OR ETF
        }

        var stock = stockInstrumentRepository.findBySymbol(record.getIslemKodu(), BIST.getId())
                .orElse(new StockInstrument(
                        record.bultenAdi(),
                        record.getIslemKodu(),
                        BIST
                ));

        stockInstrumentRepository.save(stock);

        return true;
    }

    // Manual CSV parsing replacing Spring Batch FlatFileItemReader
    private List<BistImportStockCsvRecord> readCsv(Path path) throws IOException {
        List<BistImportStockCsvRecord> list = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (lineNo <= 2) { // Skip first 2 header lines just like linesToSkip(2)
                    continue;
                }
                // Split by semicolon, keep empty tokens
                String[] cols = line.split(";", -1);
                // Validate minimum columns for indices used (we access up to index 23)
                if (cols.length <= 23) {
                    log.debug("Skipping line {} due to insufficient columns (found {}, need > 23).", lineNo, cols.length);
                    continue;
                }
                try {
                    BistImportStockCsvRecord record = new BistImportStockCsvRecord(
                            safeString(cols[1]),
                            safeString(cols[2]),
                            safeString(cols[6]),
                            safeString(cols[7]),
                            safeBigDecimal(cols[21]),
                            safeBigDecimal(cols[16]),
                            safeBigDecimal(cols[23])
                    );
                    list.add(record);
                } catch (Exception ex) {
                    log.warn("Failed to parse line {}. Content: {}", lineNo, line, ex);
                }
            }
        }
        log.info("Parsed {} records from {}", list.size(), path.toAbsolutePath());
        return list;
    }

    private static String safeString(String v) {
        return v == null ? null : v.trim();
    }

    private static BigDecimal safeBigDecimal(String v) {
        if (v == null) return BigDecimal.ZERO;
        v = v.trim();
        if (v.isEmpty()) return BigDecimal.ZERO;
        // Turkish locale often uses comma as decimal separator
        v = v.replace(',', '.');
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO; // Fallback, already logged by caller if necessary
        }
    }
}
