package com.example.shared.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность для хранения исторических данных баланса портфолио
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "portfolio_history")
public class PortfolioHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Время снапшота баланса
     */
    @Column(name = "snapshot_time", nullable = false)
    private LocalDateTime snapshotTime;

    /**
     * Общий баланс портфолио в USDT
     */
    @Column(name = "total_balance", precision = 20, scale = 8)
    private BigDecimal totalBalance;

    /**
     * Доступный баланс в USDT
     */
    @Column(name = "available_balance", precision = 20, scale = 8)
    private BigDecimal availableBalance;

    /**
     * Зарезервированный баланс в позициях в USDT
     */
    @Column(name = "reserved_balance", precision = 20, scale = 8)
    private BigDecimal reservedBalance;

    /**
     * Нереализованная прибыль/убыток в USDT
     */
    @Column(name = "unrealized_pnl", precision = 20, scale = 8)
    private BigDecimal unrealizedPnL;

    /**
     * Реализованная прибыль/убыток в USDT
     */
    @Column(name = "realized_pnl", precision = 20, scale = 8)
    private BigDecimal realizedPnL;

    /**
     * Количество активных позиций
     */
    @Column(name = "active_positions_count")
    private Integer activePositionsCount;

    /**
     * Тип провайдера торговли
     */
    @Column(name = "provider_type", length = 50)
    private String providerType;

    /**
     * Конструктор для создания записи на основе Portfolio DTO
     */
    public PortfolioHistory(com.example.shared.dto.Portfolio portfolio, String providerType) {
        this.snapshotTime = LocalDateTime.now();
        this.totalBalance = portfolio.getTotalBalance();
        this.availableBalance = portfolio.getAvailableBalance();
        this.reservedBalance = portfolio.getReservedBalance();
        this.unrealizedPnL = portfolio.getUnrealizedPnL();
        this.realizedPnL = portfolio.getRealizedPnL();
        this.activePositionsCount = portfolio.getActivePositionsCount();
        this.providerType = providerType;
    }

    /**
     * Получить временную метку в миллисекундах
     */
    public long getTimestampMillis() {
        return snapshotTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}