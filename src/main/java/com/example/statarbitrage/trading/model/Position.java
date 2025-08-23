package com.example.statarbitrage.trading.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Column(name = "position_id")
    private String positionId; // Реальный ID позиции от OKX (например "2716523748303249408")

    @Column(name = "pair_data_id")
    private Long pairDataId;

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

    @Deprecated
    public void calculateUnrealizedPnL() {
        if (entryPrice == null || currentPrice == null || size == null || size.compareTo(BigDecimal.ZERO) == 0) {
            unrealizedPnLUSDT = BigDecimal.ZERO;
            unrealizedPnLPercent = BigDecimal.ZERO;
            return;
        }

        BigDecimal safeOpeningFees = openingFees != null ? openingFees : BigDecimal.ZERO;
        BigDecimal safeFundingFees = fundingFees != null ? fundingFees : BigDecimal.ZERO;
        BigDecimal totalFees = safeOpeningFees.subtract(safeFundingFees);
        this.unrealizedPnLUSDT = unrealizedPnLUSDT.subtract(totalFees);

        log.debug("📊 Расчет PnL {}:", symbol);
        log.debug("➡️ OpeningFees: {}", safeOpeningFees);
        log.debug("➡️ FundingFees: {}", safeFundingFees);
        log.debug("➡️ TotalFees: {}", totalFees);
        log.debug("✅ UnrealizedPnL (после вычета комиссий): {} USDT", this.unrealizedPnLUSDT);

        if (allocatedAmount != null && allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.unrealizedPnLPercent = this.unrealizedPnLUSDT.divide(allocatedAmount, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            log.debug("✅ UnrealizedPnL: {} % (на сумму вложений {})", this.unrealizedPnLPercent, allocatedAmount);
        } else {
            this.unrealizedPnLPercent = BigDecimal.ZERO;
            log.warn("⚠️ allocatedAmount = null или 0, процентный PnL не вычислен.");
        }
    }

    @Deprecated
    public void calculateAndSetRealizedPnL(BigDecimal closedPnlUSDT, BigDecimal closingFees) {
        if (entryPrice == null || closedPnlUSDT == null || size == null || size.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("❌ Недостаточно данных для расчета реализованного PnL: entryPrice={}, closedPnlUSDT={}, size={}", entryPrice, closedPnlUSDT, size);
            this.realizedPnLUSDT = BigDecimal.ZERO;
            this.realizedPnLPercent = BigDecimal.ZERO;
            return;
        }

        BigDecimal safeOpeningFees = openingFees != null ? openingFees : BigDecimal.ZERO;
        BigDecimal safeClosingFees = closingFees != null ? closingFees : BigDecimal.ZERO;
        BigDecimal safeFundingFees = fundingFees != null ? fundingFees : BigDecimal.ZERO;
        BigDecimal totalFees = safeOpeningFees.add(safeClosingFees).subtract(safeFundingFees);
        this.realizedPnLUSDT = closedPnlUSDT.subtract(totalFees);
        this.closingFees = safeClosingFees;

        log.debug("📊 Расчет PnL {}:", symbol);
        log.debug("➡️ ClosedPnL (без комиссий): {}", closedPnlUSDT);
        log.debug("➡️ OpeningFees: {}", safeOpeningFees);
        log.debug("➡️ ClosingFees: {}", safeClosingFees);
        log.debug("➡️ FundingFees: {}", safeFundingFees);
        log.debug("➡️ TotalFees: {}", totalFees);
        log.debug("✅ RealizedPnL (после вычета комиссий): {} USDT", this.realizedPnLUSDT);

        if (allocatedAmount != null && allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.realizedPnLPercent = this.realizedPnLUSDT
                    .divide(allocatedAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            log.debug("✅ RealizedPnL: {} % (на сумму вложений {})", this.realizedPnLPercent, allocatedAmount);
        } else {
            this.realizedPnLPercent = BigDecimal.ZERO;
            log.warn("⚠️ allocatedAmount = null или 0, процентный PnL не вычислен.");
        }

        this.unrealizedPnLUSDT = BigDecimal.ZERO;
        this.unrealizedPnLPercent = BigDecimal.ZERO;
        log.debug("♻️ UnrealizedPnL сброшен до нуля, позиция закрыта.");
    }

    public boolean isOpen() {
        return status == PositionStatus.OPEN;
    }

    public String getDirectionString() {
        return type == PositionType.LONG ? "LONG" : "SHORT";
    }
}
