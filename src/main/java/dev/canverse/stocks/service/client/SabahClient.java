package dev.canverse.stocks.service.client;

import dev.canverse.stocks.service.client.model.CanliBorsaVerileri;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class SabahClient {
    private final RestTemplate client;

    public SabahClient() {
        this.client = new RestTemplate();
    }

    public CanliBorsaVerileri fetchBIST() {
        var result = client.getForObject("https://www.sabah.com.tr/json/canli-borsa-verileri", String.class);

        if (result == null) {
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
                stockDataList.add(new CanliBorsaVerileri.Item(
                        fields[0],
                        new BigDecimal(fields[1].replace(',', '.')),
                        new BigDecimal(fields[2].replace(',', '.')),
                        Instant.now(),
                        new BigDecimal(fields[4].replace(',', '.')),
                        fields[5]));
            }
        }

        return new CanliBorsaVerileri(stockDataList);
    }
}
