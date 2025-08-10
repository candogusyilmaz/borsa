package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.portfolio.Transaction;
import dev.canverse.stocks.repository.custom.TransactionRepositoryCustom;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends BaseJpaRepository<Transaction, Long>, TransactionRepositoryCustom {
}