package com.example.shared.models;

import com.example.shared.enums.PositionStatus;
import com.example.shared.enums.PositionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "positions")
public class Position {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "position_id", nullable = false, unique = true) //todo сделать nullable=false и может unique=true
    private String positionId; // Реальный ID позиции от OKX (например "2716523748303249408")

    @Column(name = "trading_pair_id", nullable = false)
    private Long tradingPairId;

    @Column(name = "symbol")
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private PositionType type;

    @Column(name = "size", precision = 19, scale = 8)
    private BigDecimal size;

    @Column(name = "entry_price", precision = 19, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "closing_price", precision = 19, scale = 8)
    private BigDecimal closingPrice;

    @Column(name = "current_price", precision = 19, scale = 8)
    private BigDecimal currentPrice;

    @Column(name = "leverage", precision = 19, scale = 8)
    private BigDecimal leverage;

    @Column(name = "allocated_amount", precision = 19, scale = 8)
    private BigDecimal allocatedAmount;

    @Column(name = "unrealized_pnl_usdt", precision = 19, scale = 8)
    private BigDecimal unrealizedPnLUSDT;

    @Column(name = "unrealized_pnl_percent", precision = 19, scale = 8)
    private BigDecimal unrealizedPnLPercent;

    @Column(name = "realized_pnl_usdt", precision = 19, scale = 8)
    private BigDecimal realizedPnLUSDT;

    @Column(name = "realized_pnl_percent", precision = 19, scale = 8)
    private BigDecimal realizedPnLPercent;

    @Column(name = "opening_fees", precision = 19, scale = 8)
    private BigDecimal openingFees;

    @Column(name = "funding_fees", precision = 19, scale = 8)
    private BigDecimal fundingFees;

    @Column(name = "closing_fees", precision = 19, scale = 8)
    private BigDecimal closingFees;

    @Column(name = "open_close_fees", precision = 19, scale = 8)
    private BigDecimal openCloseFees;

    @Column(name = "open_close_funding_fees", precision = 19, scale = 8)
    private BigDecimal openCloseFundingFees;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PositionStatus status;

    @Column(name = "open_time")
    private LocalDateTime openTime;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "external_order_id")
    private String externalOrderId;

    @Column(name = "timeframe", length = 10)
    private String timeframe; // ТФ: 1m, 5m, 15m, 1H, 4H, 1D, 1W, 1M

    @Column(name = "candle_count")
    private Integer candleCount; // Свечей: количество свечей использованных для анализа

    public boolean isOpen() {
        return status == PositionStatus.OPEN;
    }

    public String getDirectionString() {
        return type == PositionType.LONG ? "LONG" : "SHORT";
    }
}
