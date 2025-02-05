package dev.canverse.stocks.dataloader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.canverse.stocks.domain.entity.Country;
import dev.canverse.stocks.domain.entity.Exchange;
import dev.canverse.stocks.repository.CountryRepository;
import dev.canverse.stocks.repository.ExchangeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

@Order(1)
@Component
@RequiredArgsConstructor
public class Setup implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(Setup.class);

    private final ObjectMapper mapper;
    private final CountryRepository countryRepository;
    private final ExchangeRepository exchangeRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        setupCountries();
        setupExchanges();
    }

    private void setupCountries() {
        if (countryRepository.count() > 0) {
            log.info("Countries data already loaded.");
            return;
        }

        var resource = new ClassPathResource("/data/countries.json");

        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            var countries = mapper.readValue(reader, new TypeReference<List<Country>>() {
            });
            countryRepository.saveAllAndFlush(countries);
        } catch (IOException e) {
            log.error("Failed to load stocks data", e);
        } finally {
            entityManager.clear();
        }
    }

    private void setupExchanges() {
        if (exchangeRepository.count() > 0) {
            log.info("Exchanges data already loaded.");
            return;
        }

        var resource = new ClassPathResource("/data/exchanges.json");

        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            var countries = mapper.readValue(reader, new TypeReference<List<Exchange>>() {
            });
            exchangeRepository.saveAllAndFlush(countries);
        } catch (IOException e) {
            log.error("Failed to load stocks data", e);
        } finally {
            entityManager.clear();
        }
    }
}

