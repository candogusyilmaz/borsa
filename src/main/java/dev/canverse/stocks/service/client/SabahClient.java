package dev.canverse.stocks.service.client;

import dev.canverse.stocks.service.client.model.CanliBorsaVerileri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class SabahClient {
    private static final Logger log = LoggerFactory.getLogger(SabahClient.class);
    private final RestTemplate client;

    public SabahClient() {
        this.client = new RestTemplate();
    }

    public CanliBorsaVerileri fetchBIST() {
        String result = null;

        try {
            result = client.getForObject("https://www.sabah.com.tr/json/canli-borsa-verileri", String.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch BIST data due to RestClientException.", e);
        }

        if (result == null) {
            log.error("Error while fetching BIST data. Returned data is null. Cannot parse or save BIST data. Skipping.");
            return new CanliBorsaVerileri(List.of());
        }

        return parseStockData(result);
    }

    private CanliBorsaVerileri parseStockData(String data) {
        var stockDataList = new ArrayList<CanliBorsaVerileri.Item>();

        // Remove the callbackRT wrapper and split the data into individual entries
        String cleanedData = data.replace("callbackRT({ \"data\" : \"", "").replace("\"});", "");
        String[] entries = Arrays.stream(cleanedData.split("~")).skip(1).toArray(String[]::new);

        // Parse each entry
        for (String entry : entries) {
            if (entry.isEmpty()) continue; // Skip empty entries

            String[] fields = entry.split("\\|");
            if (fields.length >= 6) { // Ensure there are enough fields

                var formatter = DateTimeFormatter.ofPattern("HH:mm");
                var time = LocalTime.parse(fields[3], formatter);

                // Combine it with today's date and convert to Instant
                Instant timeInstant = time.atDate(LocalDate.now())
                        .atZone(ZoneId.of("Europe/Istanbul"))
                        .toInstant();

                stockDataList.add(new CanliBorsaVerileri.Item(
                        fields[0],
                        new BigDecimal(fields[1].replace(',', '.')),
                        new BigDecimal(fields[4].replace(',', '.')),
                        timeInstant,
                        new BigDecimal(fields[2].replace(',', '.')),
                        fields[5]));
            }
        }

        return new CanliBorsaVerileri(stockDataList);
    }
}
