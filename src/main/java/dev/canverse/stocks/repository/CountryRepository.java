package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Country;
import org.springframework.stereotype.Repository;

@Repository
public interface CountryRepository extends BaseJpaRepository<Country, Long> {
    Country findByIsoCode(String isoCode);
}