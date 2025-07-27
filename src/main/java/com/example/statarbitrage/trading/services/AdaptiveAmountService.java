package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.interfaces.TradingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveAmountService {
    /**
     * Адаптивный расчет сумм для минимизации дисбаланса после lot size корректировки
     */
    public BigDecimal[] calculate(TradingProvider provider, PairData pairData, BigDecimal totalAmount) {
        try {
            // Получаем текущие цены для расчета lot size
            BigDecimal longPrice = provider.getCurrentPrice(pairData.getLongTicker());
            BigDecimal shortPrice = provider.getCurrentPrice(pairData.getShortTicker());

            if (longPrice == null || shortPrice == null ||
                    longPrice.compareTo(BigDecimal.ZERO) <= 0 || shortPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Не удалось получить цены для адаптивного расчета, используем 50/50");
                BigDecimal half = totalAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                return new BigDecimal[]{half, half};
            }

            // Пытаемся найти оптимальное распределение
            BigDecimal bestLongAmount = null;
            BigDecimal bestShortAmount = null;
            BigDecimal minDifference = BigDecimal.valueOf(Double.MAX_VALUE);

            // Пробуем разные распределения от 40% до 60% для long позиции
            for (int longPercent = 40; longPercent <= 60; longPercent++) {
                BigDecimal longAmount = totalAmount.multiply(BigDecimal.valueOf(longPercent))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal shortAmount = totalAmount.subtract(longAmount);

                // Симулируем корректировку lot size
                BigDecimal longSizeBeforeAdjust = longAmount.divide(longPrice, 8, RoundingMode.HALF_UP);
                BigDecimal shortSizeBeforeAdjust = shortAmount.divide(shortPrice, 8, RoundingMode.HALF_UP);

                // Применяем примерную корректировку (округление до целых)
                BigDecimal adjustedLongSize = longSizeBeforeAdjust.setScale(0, RoundingMode.DOWN);
                BigDecimal adjustedShortSize = shortSizeBeforeAdjust.setScale(0, RoundingMode.DOWN);

                // Рассчитываем итоговые суммы после корректировки
                BigDecimal adjustedLongAmount = adjustedLongSize.multiply(longPrice);
                BigDecimal adjustedShortAmount = adjustedShortSize.multiply(shortPrice);

                // Считаем разность
                BigDecimal difference = adjustedLongAmount.subtract(adjustedShortAmount).abs();

                if (difference.compareTo(minDifference) < 0) {
                    minDifference = difference;
                    bestLongAmount = longAmount;
                    bestShortAmount = shortAmount;
                }
            }

            if (bestLongAmount != null && bestShortAmount != null) {
                log.info("🎯 Найдено оптимальное распределение: LONG ${}, SHORT ${} (ожидаемая разность после корректировки: ${})",
                        bestLongAmount, bestShortAmount, minDifference);
                return new BigDecimal[]{bestLongAmount, bestShortAmount};
            }

        } catch (Exception e) {
            log.warn("⚠️ Ошибка при адаптивном расчете: {}, используем 50/50", e.getMessage());
        }

        // Fallback к равному распределению
        BigDecimal half = totalAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new BigDecimal[]{half, half};
    }
}
