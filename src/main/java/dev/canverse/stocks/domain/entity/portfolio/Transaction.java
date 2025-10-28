package dev.canverse.stocks.domain.entity.portfolio;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import dev.canverse.stocks.domain.Calculator;
import dev.canverse.stocks.domain.valueobject.TransactionMetadata;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(schema = "portfolio", name = "transactions")
public class Transaction implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Position position;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Positive
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @Positive
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal price;

    @PositiveOrZero
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal commission;

    @PositiveOrZero
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal newQuantity;

    @PositiveOrZero
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal newTotal;

    @NotNull
    @Column(nullable = false)
    private Instant actionDate;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    private TransactionMetadata metadata = new TransactionMetadata();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @JsonManagedReference
    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL)
    @JoinColumn(name = "id", referencedColumnName = "transaction_id")
    private TransactionPerformance performance;

    protected Transaction() {
    }

    protected static Transaction buy(Position position, BigDecimal quantity, BigDecimal price, BigDecimal newQuantity, BigDecimal newTotal, Instant actionDate) {
        var transaction = new Transaction();

        transaction.position = position;
        transaction.type = Type.BUY;
        transaction.quantity = quantity;
        transaction.price = price;
        transaction.commission = BigDecimal.ZERO;
        transaction.actionDate = actionDate;
        transaction.newQuantity = newQuantity;
        transaction.newTotal = newTotal;

        return transaction;
    }

    protected static Transaction sell(Position position, BigDecimal quantity, BigDecimal price, BigDecimal newQuantity, BigDecimal newTotal, Instant actionDate) {
        var transaction = new Transaction();
        transaction.position = position;
        transaction.type = Type.SELL;
        transaction.quantity = quantity;
        transaction.price = price;
        transaction.commission = BigDecimal.ZERO;
        transaction.actionDate = actionDate;
        transaction.newQuantity = newQuantity;
        transaction.newTotal = newTotal;
        transaction.performance = new TransactionPerformance(transaction, calculateProfit(position, quantity, price), calculateReturnPercentage(position, price));

        return transaction;
    }

    private static BigDecimal calculateReturnPercentage(Position position, BigDecimal price) {
        return Calculator.divide(price.subtract(position.getAveragePrice()).multiply(BigDecimal.valueOf(100)), position.getAveragePrice());
    }

    private static BigDecimal calculateProfit(Position position, BigDecimal quantity, BigDecimal price) {
        return price.subtract(position.getAveragePrice()).multiply(quantity);
    }

    public enum Type {
        BUY,
        SELL
    }

    public BigDecimal getTotal() {
        return this.price.multiply(this.quantity);
    }
}
