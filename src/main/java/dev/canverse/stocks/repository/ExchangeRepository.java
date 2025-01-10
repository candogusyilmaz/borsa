package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Exchange;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeRepository extends BaseJpaRepository<Exchange, Long> {
    Exchange findByCode(String code);
}