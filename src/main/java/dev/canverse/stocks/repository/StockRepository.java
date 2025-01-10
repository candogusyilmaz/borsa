package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.Stock;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRepository extends BaseJpaRepository<Stock, Long> {

}