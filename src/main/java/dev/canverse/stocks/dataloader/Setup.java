package dev.canverse.stocks.dataloader;

import dev.canverse.stocks.repository.CountryRepository;
import dev.canverse.stocks.repository.ExchangeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;

@Order(1)
@Component
@RequiredArgsConstructor
public class Setup implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(Setup.class);

    private final JdbcTemplate jdbcTemplate;
    private final CountryRepository countryRepository;
    private final ExchangeRepository exchangeRepository;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        setupCountries();
        setupExchanges();
    }

    private void setupCountries() {
        if (countryRepository.count() > 0) {
            log.info("Countries data already loaded.");
            return;
        }

        try {
            // Get the SQL file from resources
            var resource = new ClassPathResource("/scripts/countries.sql");
            var sqlScript = new String(FileCopyUtils.copyToByteArray(resource.getInputStream()));
            jdbcTemplate.execute(sqlScript);
            log.info("Countries data loaded successfully.");
        } catch (IOException e) {
            log.error("Error reading SQL file: " + e.getMessage());
        }
    }

    private void setupExchanges() {
        if (exchangeRepository.count() > 0) {
            log.info("Exchanges data already loaded.");
            return;
        }

        try {
            // Get the SQL file from resources
            var resource = new ClassPathResource("/scripts/exchanges.sql");
            var sqlScript = new String(FileCopyUtils.copyToByteArray(resource.getInputStream()));
            jdbcTemplate.execute(sqlScript);
            log.info("Exchanges data loaded successfully.");
        } catch (IOException e) {
            log.error("Error reading SQL file: {}", e.getMessage());
        }
    }
}

