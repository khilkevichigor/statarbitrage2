package com.example.statarbitrage.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Модель торговой позиции (универсальная для виртуальной и реальной торговли)
 * Включает расчет как нереализованной (для открытых позиций), так и реализованной (для закрытых) прибыли.
 */
@Slf4j
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Position {

    /**
     * Уникальный идентификатор позиции
     */
    private String positionId;

    /**
     * ID пары в системе статарбитража
     */
    private Long pairDataId;

    /**
     * Символ торгового инструмента
     */
    private String symbol;

    /**
     * Тип позиции
     */
    private PositionType type;

    /**
     * Размер позиции (в базовой валюте)
     */
    private BigDecimal size;

    /**
     * Цена входа
     */
    private BigDecimal entryPrice;

    /**
     * Цена закрытия
     */
    private BigDecimal closingPrice;

    /**
     * Текущая рыночная цена
     */
    private BigDecimal currentPrice;

    /**
     * Плечо
     */
    private BigDecimal leverage;

    /**
     * Выделенная сумма из депозита (маржа)
     */
    private BigDecimal allocatedAmount;

    /**
     * Нереализованная прибыль/убыток (Net PnL)
     */
    private BigDecimal unrealizedPnLUSDT;

    /**
     * Нереализованная прибыль/убыток (%)
     */
    private BigDecimal unrealizedPnLPercent;

    /**
     * Реализованная (зафиксированная) прибыль/убыток (Net PnL)
     */
    private BigDecimal realizedPnLUSDT;

    /**
     * Реализованная прибыль/убыток (%)
     */
    private BigDecimal realizedPnLPercent;

    /**
     * Комиссии за открытие
     */
    private BigDecimal openingFees;

    /**
     * Комиссии за фандинг
     */
    private BigDecimal fundingFees;

    /**
     * Комиссии за закрытие
     */
    private BigDecimal closingFees;

    /**
     * Статус позиции
     */
    private PositionStatus status;

    /**
     * Время открытия
     */
    private LocalDateTime openTime;

    /**
     * Время последнего обновления
     */
    private LocalDateTime lastUpdated;

    /**
     * Дополнительные метаданные (JSON)
     */
    private String metadata;

    /**
     * Идентификатор внешнего ордера (для связи с биржей)
     */
    private String externalOrderId;

    /**
     * Расчет нереализованной прибыли/убытка (Net PnL).
     * Учитывает комиссию за открытие, так как она уже уплачена.
     * Комиссия за закрытие будет учтена при расчете реализованного PnL.
     */
    public void calculateUnrealizedPnL() {
        if (entryPrice == null || currentPrice == null || size == null || size.compareTo(BigDecimal.ZERO) == 0) {
            unrealizedPnLUSDT = BigDecimal.ZERO;
            unrealizedPnLPercent = BigDecimal.ZERO;
            return;
        }

        // 2. Вычитаем комиссию за открытие (она уже уплачена)
        BigDecimal safeOpeningFees = openingFees != null ? openingFees : BigDecimal.ZERO;
        BigDecimal safeFundingFees = fundingFees != null ? fundingFees : BigDecimal.ZERO;
        BigDecimal totalFees = safeOpeningFees.subtract(safeFundingFees);

        this.unrealizedPnLUSDT = unrealizedPnLUSDT.subtract(totalFees);

        log.info("📊 Расчет PnL:");
        log.info("➡️ OpeningFees: {}", safeOpeningFees);
        log.info("➡️ FundingFees: {}", safeFundingFees);
        log.info("➡️ TotalFees: {}", totalFees);
        log.info("✅ UnrealizedPnL (после вычета комиссий): {} USDT", this.realizedPnLUSDT);

        // 3. Рассчитываем процентную прибыль на основе чистого PnL
        if (allocatedAmount != null && allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.unrealizedPnLPercent = this.unrealizedPnLUSDT.divide(allocatedAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            log.info("✅ UnrealizedPnL: {} % (на сумму вложений {})", this.unrealizedPnLPercent, allocatedAmount);
        } else {
            this.unrealizedPnLPercent = BigDecimal.ZERO;
            log.warn("⚠️ allocatedAmount = null или 0, процентный PnL не вычислен.");
        }
    }

    /**
     * Расчет и установка реализованной прибыли/убытка (Net PnL) после закрытия позиции.
     *
     * @param closedPnl   pnl после закрытия без учета комиссий
     * @param closingFees Комиссия, уплаченная за закрытие позиции.
     */
    public void calculateAndSetRealizedPnL(BigDecimal closedPnl, BigDecimal closingFees) {
        if (entryPrice == null || closedPnl == null || size == null || size.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("❌ Недостаточно данных для расчета PnL: entryPrice={}, closedPnl={}, size={}", entryPrice, closedPnl, size);
            this.realizedPnLUSDT = BigDecimal.ZERO;
            this.realizedPnLPercent = BigDecimal.ZERO;
            return;
        }

        BigDecimal safeOpeningFees = openingFees != null ? openingFees : BigDecimal.ZERO;
        BigDecimal safeClosingFees = closingFees != null ? closingFees : BigDecimal.ZERO;
        BigDecimal safeFundingFees = fundingFees != null ? fundingFees : BigDecimal.ZERO;

        BigDecimal totalFees = safeOpeningFees.add(safeClosingFees).subtract(safeFundingFees);
        this.realizedPnLUSDT = closedPnl.subtract(totalFees);
        this.closingFees = safeClosingFees;

        log.info("📊 Расчет PnL:");
        log.info("➡️ ClosedPnL (без комиссий): {}", closedPnl);
        log.info("➡️ OpeningFees: {}", safeOpeningFees);
        log.info("➡️ ClosingFees: {}", safeClosingFees);
        log.info("➡️ FundingFees: {}", safeFundingFees);
        log.info("➡️ TotalFees: {}", totalFees);
        log.info("✅ RealizedPnL (после вычета комиссий): {} USDT", this.realizedPnLUSDT);

        if (allocatedAmount != null && allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.realizedPnLPercent = this.realizedPnLUSDT
                    .divide(allocatedAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            log.info("✅ RealizedPnL: {} % (на сумму вложений {})", this.realizedPnLPercent, allocatedAmount);
        } else {
            this.realizedPnLPercent = BigDecimal.ZERO;
            log.warn("⚠️ allocatedAmount = null или 0, процентный PnL не вычислен.");
        }

        this.unrealizedPnLUSDT = BigDecimal.ZERO;
        this.unrealizedPnLPercent = BigDecimal.ZERO;
        log.info("♻️ UnrealizedPnL сброшен до нуля, позиция закрыта.");
    }


    /**
     * Проверка, открыта ли позиция
     */
    public boolean isOpen() {
        return status == PositionStatus.OPEN;
    }

    /**
     * Получение направления позиции как строки
     */
    public String getDirectionString() {
        return type == PositionType.LONG ? "LONG" : "SHORT";
    }
}