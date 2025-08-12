package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Currency;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyRepository extends BaseJpaRepository<Currency, Long> {
    Currency findByCode(String code);
}