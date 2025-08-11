package dev.canverse.stocks.domain.entity.portfolio;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(schema = "portfolio", name = "position_history")
public class PositionHistory implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Position position;

    @NotNull
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal quantity;

    @NotNull
    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal total;

    @NotNull
    @Enumerated(EnumType.STRING)
    private PositionHistory.ActionType actionType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PositionHistory() {
    }

    protected PositionHistory(Position position, ActionType actionType) {
        this.position = position;
        this.quantity = position.getQuantity();
        this.total = position.getTotal();
        this.actionType = actionType;
    }

    public enum ActionType {
        BUY,
        SELL,
        UNDO,
        STOCK_SPLIT
    }
}
