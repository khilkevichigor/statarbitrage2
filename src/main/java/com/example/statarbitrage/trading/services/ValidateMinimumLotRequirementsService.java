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
public class ValidateMinimumLotRequirementsService {
    /**
     * Проверка пары на соответствие требованиям минимального лота
     * Возвращает false если минимальный лот для любой позиции превышает желаемую сумму в 3+ раза
     */
    public boolean validate(TradingProvider provider, PairData pairData, BigDecimal longAmount, BigDecimal shortAmount) {
        try {
            // Получаем текущие цены
            BigDecimal longPrice = provider.getCurrentPrice(pairData.getLongTicker());
            BigDecimal shortPrice = provider.getCurrentPrice(pairData.getShortTicker());

            if (longPrice == null || shortPrice == null ||
                    longPrice.compareTo(BigDecimal.ZERO) <= 0 || shortPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Не удалось получить цены для проверки минимальных лотов {}", pairData.getPairName());
                return true; // Позволяем торговлю если не удалось получить цены
            }

            // Проверяем LONG позицию
            if (!validatePositionForMinimumLot(pairData.getLongTicker(), longAmount, longPrice)) {
                return false;
            }

            // Проверяем SHORT позицию
            if (!validatePositionForMinimumLot(pairData.getShortTicker(), shortAmount, shortPrice)) {
                return false;
            }

            log.info("✅ Пара {} прошла проверку минимальных лотов", pairData.getPairName());
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке минимальных лотов для {}: {}", pairData.getPairName(), e.getMessage());
            return true; // Позволяем торговлю при ошибке проверки
        }
    }

    /**
     * Проверка конкретной позиции на соответствие требованиям минимального лота
     */
    private boolean validatePositionForMinimumLot(String symbol, BigDecimal desiredAmount, BigDecimal currentPrice) {
        try {
            // Рассчитываем желаемый размер позиции
            BigDecimal desiredSize = desiredAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);

            // Симулируем корректировку минимального лота (упрощенно)
            BigDecimal adjustedSize = desiredSize.setScale(0, RoundingMode.DOWN);
            if (adjustedSize.compareTo(BigDecimal.ONE) < 0) {
                adjustedSize = BigDecimal.ONE; // Минимальный лот = 1 единица
            }

            // Рассчитываем итоговую стоимость после корректировки
            BigDecimal adjustedAmount = adjustedSize.multiply(currentPrice);
            BigDecimal excessRatio = adjustedAmount.divide(desiredAmount, 4, RoundingMode.HALF_UP);

            // Если превышение больше 3x - блокируем пару
            if (excessRatio.compareTo(BigDecimal.valueOf(3.0)) > 0) {
                log.warn("❌ БЛОКИРОВКА: {} минимальный лот приводит к позиции ${{}} вместо желаемых ${{}} (превышение в {} раз)",
                        symbol, adjustedAmount, desiredAmount, excessRatio);
                return false;
            }

            log.debug("✅ {} прошел проверку: желаемая сумма=${{}}, итоговая сумма=${{}}, соотношение={}",
                    symbol, desiredAmount, adjustedAmount, excessRatio);
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке минимального лота для {}: {}", symbol, e.getMessage());
            return true; // Позволяем торговлю при ошибке
        }
    }
}
