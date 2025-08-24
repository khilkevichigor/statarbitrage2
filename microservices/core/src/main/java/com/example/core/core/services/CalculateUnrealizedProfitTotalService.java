package com.example.core.core.services;

import com.example.core.common.model.PairData;
import com.example.core.common.model.TradeStatus;
import com.example.core.core.repositories.PairDataRepository;
import com.example.core.trading.model.Position;
import com.example.core.trading.model.Positioninfo;
import com.example.core.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateUnrealizedProfitTotalService {
    private final PairDataRepository pairDataRepository;
    private final TradingIntegrationService tradingIntegrationService;

    public BigDecimal getUnrealizedProfitPercentTotal() {
        List<PairData> tradingPairs = pairDataRepository.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);

        BigDecimal totalWeightedProfit = BigDecimal.ZERO;
        BigDecimal totalAllocatedAmount = BigDecimal.ZERO;

        for (PairData pair : tradingPairs) {
            try {
                // Получаем информацию о позициях для текущей пары
                Positioninfo positionInfo = tradingIntegrationService.getPositionInfo(pair);

                if (positionInfo == null) {
                    log.warn("⚠️ Не найдена информация о позициях для пары {}", pair.getPairName());
                    continue;
                }

                Position longPosition = positionInfo.getLongPosition();
                Position shortPosition = positionInfo.getShortPosition();

                if (longPosition == null || shortPosition == null) {
                    log.warn("⚠️ Не найдены позиции для пары {}: long={}, short={}",
                            pair.getPairName(), longPosition != null, shortPosition != null);
                    continue;
                }

                // Безопасное получение allocated amounts
                BigDecimal longAlloc = safeGet(longPosition.getAllocatedAmount());
                BigDecimal shortAlloc = safeGet(shortPosition.getAllocatedAmount());
                BigDecimal pairTotalAlloc = longAlloc.add(shortAlloc);

                if (pairTotalAlloc.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("⚠️ Нулевое allocatedAmount для пары {}", pair.getPairName());
                    continue;
                }

                // Взвешенный процентный профит для пары: (P1 * A1 + P2 * A2) / (A1 + A2)
                BigDecimal longPnlPercent = safeGet(longPosition.getUnrealizedPnLPercent());
                BigDecimal shortPnlPercent = safeGet(shortPosition.getUnrealizedPnLPercent());

                BigDecimal pairWeightedPnlPercent = longPnlPercent.multiply(longAlloc)
                        .add(shortPnlPercent.multiply(shortAlloc))
                        .divide(pairTotalAlloc, 8, RoundingMode.HALF_UP);

                // Добавляем к общим суммам
                totalWeightedProfit = totalWeightedProfit.add(pairWeightedPnlPercent.multiply(pairTotalAlloc));
                totalAllocatedAmount = totalAllocatedAmount.add(pairTotalAlloc);

                log.debug("📊 Пара {}: allocatedAmount={}, weightedPnL%={}",
                        pair.getPairName(), pairTotalAlloc, pairWeightedPnlPercent);

            } catch (Exception e) {
                log.error("❌ Ошибка при расчете профита для пары {}: {}", pair.getPairName(), e.getMessage());
            }
        }

        // Общий взвешенный процентный профит
        if (totalAllocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal result = totalWeightedProfit.divide(totalAllocatedAmount, 8, RoundingMode.HALF_UP);
            log.debug("✅ Общий взвешенный PnL%: {} (totalAllocated: {})", result, totalAllocatedAmount);
            return result;
        } else {
            log.debug("⚠️ Общий allocatedAmount равен нулю");
            return BigDecimal.ZERO;
        }
    }

    /**
     * Расчет взвешенного процентного профита для конкретной пары позиций
     * Формула: (PnL%_long * allocation_long + PnL%_short * allocation_short) / (allocation_long + allocation_short)
     */
    public BigDecimal getPairUnrealizedProfitPercentTotal(Position longPosition, Position shortPosition) {
        if (longPosition == null || shortPosition == null) {
            log.warn("⚠️ Одна из позиций равна null: long={}, short={}", longPosition != null, shortPosition != null);
            return BigDecimal.ZERO;
        }

        // Безопасное получение allocated amounts
        BigDecimal longAlloc = safeGet(longPosition.getAllocatedAmount());
        BigDecimal shortAlloc = safeGet(shortPosition.getAllocatedAmount());
        BigDecimal totalAlloc = longAlloc.add(shortAlloc);

        if (totalAlloc.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("⚠️ Нулевое allocatedAmount для пары: long={}, short={}", longAlloc, shortAlloc);
            return BigDecimal.ZERO;
        }

        // Безопасное получение процентных PnL
        BigDecimal longPnlPercent = safeGet(longPosition.getUnrealizedPnLPercent());
        BigDecimal shortPnlPercent = safeGet(shortPosition.getUnrealizedPnLPercent());

        // Взвешенный процентный профит: (P1 * A1 + P2 * A2) / (A1 + A2)
        BigDecimal weightedPnlPercent = longPnlPercent.multiply(longAlloc)
                .add(shortPnlPercent.multiply(shortAlloc))
                .divide(totalAlloc, 8, RoundingMode.HALF_UP);

        log.debug("📊 Взвешенный PnL% для пары: long={}% ({}), short={}% ({}) -> result={}%",
                longPnlPercent, longAlloc, shortPnlPercent, shortAlloc, weightedPnlPercent);

        return weightedPnlPercent;
    }

    /**
     * Безопасное получение значения с заменой null на ZERO
     */
    private BigDecimal safeGet(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public BigDecimal getUnrealizedProfitUSDTTotal() {
        List<PairData> tradingPairs = pairDataRepository.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        return tradingPairs.stream()
                .map(PairData::getProfitUSDTChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
