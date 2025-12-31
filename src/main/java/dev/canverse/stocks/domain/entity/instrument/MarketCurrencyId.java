package dev.canverse.stocks.domain.entity.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
public class MarketCurrencyId implements Serializable {
    private static final long serialVersionUID = 7256167184386678512L;
    @NotNull
    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Size(max = 3)
    @NotNull
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    protected MarketCurrencyId() {
    }

    public MarketCurrencyId(Long marketId, String currencyCode) {
        this.marketId = marketId;
        this.currencyCode = currencyCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        MarketCurrencyId entity = (MarketCurrencyId) o;
        return Objects.equals(this.currencyCode, entity.currencyCode) &&
                Objects.equals(this.marketId, entity.marketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currencyCode, marketId);
    }

}