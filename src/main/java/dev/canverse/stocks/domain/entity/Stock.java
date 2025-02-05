package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Entity
@Table(name = "stocks")
public class Stock implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Exchange exchange;

    @NotEmpty
    @Column(nullable = false)
    private String name;

    @NotEmpty
    @Column(nullable = false)
    private String symbol;

    @Column(unique = true, length = 12)
    private String isin;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Country country;

    @Setter
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToOne(mappedBy = "stock", cascade = CascadeType.ALL)
    @JoinColumn(name = "id", referencedColumnName = "stock_id")
    private StockSnapshot snapshot;

    protected Stock() {
    }

    public Stock(Exchange exchange, String name, String symbol, String isin, Country country) {
        this.exchange = exchange;
        this.name = name;
        this.symbol = symbol;
        this.isin = isin;
        this.country = country;
        //this.snapshot = new StockSnapshot(this);
    }

    public Stock(Exchange exchange, String name, String symbol, String isin, Country country, String description) {
        this(exchange, name, symbol, isin, country);
        this.description = description;
    }
}
