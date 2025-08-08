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
     * Проверка пары на соответствие требованиям минимального лота.
     * Возвращает false, если минимальный лот для любой позиции превышает желаемую сумму более чем в 3 раза.
     */
    public boolean validate(TradingProvider provider, PairData pairData, BigDecimal longAmount, BigDecimal shortAmount) {
        try {
            BigDecimal longPrice = provider.getCurrentPrice(pairData.getLongTicker());
            BigDecimal shortPrice = provider.getCurrentPrice(pairData.getShortTicker());

            if (isInvalidPrice(longPrice) || isInvalidPrice(shortPrice)) {
                log.warn("⚠️ Не удалось получить корректные цены для проверки минимальных лотов для пары {}", pairData.getPairName());
                return true; // Разрешаем торговлю при отсутствии корректных данных о ценах
            }

            if (!validatePositionForMinimumLot(pairData.getLongTicker(), longAmount, longPrice)) {
                return false;
            }

            if (!validatePositionForMinimumLot(pairData.getShortTicker(), shortAmount, shortPrice)) {
                return false;
            }

            log.debug("✅ Пара {} прошла проверку минимальных лотов", pairData.getPairName());
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке минимальных лотов для {}: {}", pairData.getPairName(), e.getMessage(), e);
            return true; // Разрешаем торговлю при ошибке проверки
        }
    }

    private boolean isInvalidPrice(BigDecimal price) {
        return price == null || price.compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Проверка конкретной позиции на соответствие требованиям минимального лота.
     */
    private boolean validatePositionForMinimumLot(String symbol, BigDecimal desiredAmount, BigDecimal currentPrice) {
        try {
            BigDecimal desiredSize = desiredAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);

            // Минимальный лот округляем вниз до целого, минимум 1
            BigDecimal adjustedSize = desiredSize.setScale(0, RoundingMode.DOWN);
            if (adjustedSize.compareTo(BigDecimal.ONE) < 0) {
                adjustedSize = BigDecimal.ONE;
            }

            BigDecimal adjustedAmount = adjustedSize.multiply(currentPrice);
            BigDecimal excessRatio = adjustedAmount.divide(desiredAmount, 4, RoundingMode.HALF_UP);

            if (excessRatio.compareTo(BigDecimal.valueOf(3)) > 0) {
                log.warn("❌ БЛОКИРОВКА: {} минимальный лот требует сумму {} вместо желаемой {} (превышение в {} раз)",
                        symbol, adjustedAmount, desiredAmount, excessRatio);
                return false;
            }

            log.debug("✅ {} прошел проверку: желаемая сумма = {}, итоговая сумма = {}, соотношение = {}",
                    symbol, desiredAmount, adjustedAmount, excessRatio);
            return true;

        } catch (Exception e) {
            log.error("❌ Ошибка при проверке минимального лота для {}: {}", symbol, e.getMessage(), e);
            return true;
        }
    }
}