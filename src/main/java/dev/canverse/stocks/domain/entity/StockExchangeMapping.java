package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "stock_exchange_mappings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stock_id", "exchange_id"})
})
public class StockExchangeMapping implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Stock stock;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Exchange exchange;

    @Setter
    private ZonedDateTime listingDate;

    @Setter
    private ZonedDateTime delistedDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected StockExchangeMapping() {
    }

    public StockExchangeMapping(Stock stock, Exchange exchange) {
        this.stock = stock;
        this.exchange = exchange;
    }

    public StockExchangeMapping(Stock stock, Exchange exchange, ZonedDateTime listingDate) {
        this(stock, exchange);
        this.listingDate = listingDate;
    }
}
