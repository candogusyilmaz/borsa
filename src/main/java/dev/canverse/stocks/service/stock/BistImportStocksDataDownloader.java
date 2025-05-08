package dev.canverse.stocks.service.stock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class BistImportStocksDataDownloader implements Tasklet {

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        var date = LocalDate.now().getDayOfWeek().equals(DayOfWeek.MONDAY)
                ? LocalDate.now().minusDays(3)
                : LocalDate.now().minusDays(1);

        var url = buildUrl(date);
        log.info("Tasklet execution started. Generated URL for download: {}", url);

        var downloadDir = Paths.get("downloaded"); // Relative to WORKDIR

        try {
            Files.createDirectories(downloadDir);
            log.info("Successfully created/ensured directory: {}", downloadDir.toAbsolutePath());
            downloadAndExtractZip(url, downloadDir);
        } catch (IOException e_io_create_dir) {
            log.error("Failed to create download directory: {}", downloadDir.toAbsolutePath(), e_io_create_dir);
            // Mark step as failed or handle appropriately
            contribution.setExitStatus(ExitStatus.FAILED);
            return RepeatStatus.FINISHED;
        } catch (Exception e) { // Catch exceptions from downloadAndExtractZip if it rethrows them
            log.error("Exception during execute method, potentially from downloadAndExtractZip.", e);
            contribution.setExitStatus(ExitStatus.FAILED);
            return RepeatStatus.FINISHED;
        }

        log.info("Tasklet execution finished.");
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

    private static void downloadAndExtractZip(String urlStr, Path downloadDir) {
        log.info("Attempting to download and extract from URL: {}", urlStr);
        log.info("Target download directory (absolute): {}", downloadDir.toAbsolutePath()); // Log absolute path

        try {
            var url = URI.create(urlStr).toURL();
            // It's crucial to see if openStream() itself fails
            try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                 ZipInputStream zis = new ZipInputStream(in)) {

                log.info("Successfully opened stream to URL: {}", urlStr);
                ZipEntry entry;
                boolean csvFileProcessed = false;
                while ((entry = zis.getNextEntry()) != null) {
                    log.debug("Processing ZIP entry: {}", entry.getName());
                    if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".csv")) {
                        Path outputPath = downloadDir.resolve("stocks.csv");
                        log.info("Identified CSV file in ZIP: {}. Target output path: {}", entry.getName(), outputPath.toAbsolutePath());

                        if (Files.exists(outputPath)) {
                            log.warn("Output file {} already exists. Deleting...", outputPath);
                            try {
                                Files.delete(outputPath);
                                log.info("Successfully deleted existing file: {}", outputPath);
                            } catch (IOException e_delete) {
                                log.error("Failed to delete existing file: {}. Stopping extraction for this entry.", outputPath, e_delete);
                                // Depending on requirements, you might want to throw e_delete or just skip this entry
                                continue;
                            }
                        }

                        try {
                            long bytesCopied = Files.copy(zis, outputPath);
                            log.info("Successfully copied {} bytes from ZIP entry {} to {}", bytesCopied, entry.getName(), outputPath);
                            if (bytesCopied == 0 && entry.getSize() != 0) {
                                log.warn("Copied 0 bytes for entry {}, but entry size was reported as {}. The file might be empty in the ZIP.", entry.getName(), entry.getSize());
                            }
                            csvFileProcessed = true;
                        } catch (IOException e_copy) {
                            log.error("IOException during Files.copy from ZIP entry {} to {}.", entry.getName(), outputPath, e_copy);
                            // Re-throw or handle as appropriate
                            throw e_copy;
                        }
                        // If you only expect one CSV, you might break here
                        // break;
                    }
                    zis.closeEntry(); // Close current entry before getting the next one
                }
                if (!csvFileProcessed) {
                    log.warn("No .csv file was found or processed in the zip archive from URL: {}", urlStr);
                }

            } catch (java.net.UnknownHostException e_uhe) {
                log.error("Network Error: Unknown host for URL {}. Check DNS resolution and internet connectivity within the Docker container.", urlStr, e_uhe);
            } catch (java.net.ConnectException e_ce) {
                log.error("Network Error: Connection refused for URL {}. Ensure the remote server is up and no firewall is blocking the container's outbound connection.", urlStr, e_ce);
            } catch (javax.net.ssl.SSLHandshakeException e_ssle) {
                log.error("SSL/TLS Error: SSLHandshakeException for URL {}. The Docker container's Java environment might be missing necessary CA certificates, or there's a TLS version mismatch.", urlStr, e_ssle);
            } catch (java.io.FileNotFoundException e_fnfe) {
                log.error("File Not Found Error: The URL {} likely returned a 404. The file might not exist at the source at this time. Double-check the generated URL and file availability.", urlStr, e_fnfe);
            } catch (IOException e_io) {
                log.error("General IOException during download or processing of ZIP from URL {}. Target directory: {}", urlStr, downloadDir.toAbsolutePath(), e_io);
            }
        } catch (java.net.MalformedURLException e_murle) {
            log.error("Configuration Error: Malformed URL generated: {}.", urlStr, e_murle);
        } catch (Exception e_general) {
            // Catch any other unexpected exceptions
            log.error("An unexpected error occurred in downloadAndExtractZip for URL {}. Target directory: {}", urlStr, downloadDir.toAbsolutePath(), e_general);
        }
    }
}