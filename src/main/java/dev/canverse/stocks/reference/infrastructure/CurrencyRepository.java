package dev.canverse.stocks.reference.infrastructure;

import dev.canverse.stocks.reference.domain.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String> {}
