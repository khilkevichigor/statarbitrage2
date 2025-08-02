package com.example.statarbitrage.trading.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Модель портфолио для единого управления депо
 */
@Entity
@Table(name = "portfolio")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Общий баланс портфолио (включая позиции)
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal totalBalance;

    /**
     * Доступный баланс для новых позиций
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal availableBalance;

    /**
     * Зарезервированный баланс в открытых позициях
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal reservedBalance;

    /**
     * Начальный баланс (для расчета общего ROI)
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal initialBalance;

    /**
     * Общая нереализованная прибыль/убыток
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal unrealizedPnL;

    /**
     * Общая реализованная прибыль/убыток
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal realizedPnL;

    /**
     * Накопленные комиссии
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal totalFeesAccrued;

    /**
     * Максимальная просадка (%)
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal maxDrawdown;

    /**
     * Максимальный баланс в истории
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal highWaterMark;

    /**
     * Количество активных позиций
     */
    private Integer activePositionsCount;

    /**
     * Время последнего обновления
     */
    private LocalDateTime lastUpdated;

    /**
     * Время создания портфолио
     */
    private LocalDateTime createdAt;

    private boolean isVirtual;

    // Расчетные методы

    /**
     * Общая прибыль/убыток (реализованная + нереализованная)
     */
    public BigDecimal getTotalPnL() {
        BigDecimal realized = realizedPnL != null ? realizedPnL : BigDecimal.ZERO;
        BigDecimal unrealized = unrealizedPnL != null ? unrealizedPnL : BigDecimal.ZERO;
        return realized.add(unrealized);
    }

    /**
     * Общая доходность портфолио (%)
     */
    public BigDecimal getTotalReturn() {
        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getTotalPnL()
                .divide(initialBalance, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Использование депозита (%)
     */
    public BigDecimal getDepositUtilization() {
        if (totalBalance == null || totalBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal reserved = reservedBalance != null ? reservedBalance : BigDecimal.ZERO;
        return reserved
                .divide(totalBalance, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Текущий эквити (баланс + нереализованная прибыль)
     */
    public BigDecimal getCurrentEquity() {
        BigDecimal balance = totalBalance != null ? totalBalance : BigDecimal.ZERO;
        BigDecimal unrealized = unrealizedPnL != null ? unrealizedPnL : BigDecimal.ZERO;
        return balance.add(unrealized);
    }

    /**
     * Обновление максимальной просадки
     */
    public void updateMaxDrawdown() {
        BigDecimal currentEquity = getCurrentEquity();

        // Обновляем максимум
        if (highWaterMark == null || currentEquity.compareTo(highWaterMark) > 0) {
            highWaterMark = currentEquity;
        }

        // Рассчитываем текущую просадку
        if (highWaterMark.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentDrawdown = highWaterMark.subtract(currentEquity)
                    .divide(highWaterMark, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (maxDrawdown == null || currentDrawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = currentDrawdown;
            }
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}