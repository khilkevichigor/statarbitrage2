package com.example.core.trading.services;

import com.example.core.trading.interfaces.TradingProvider;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveAmountService {

    private static final BigDecimal PERCENT_100 = BigDecimal.valueOf(100);
    private static final BigDecimal PERCENT_START = BigDecimal.valueOf(40);
    private static final BigDecimal PERCENT_END = BigDecimal.valueOf(60);
    private static final int SCALE_PRICE = 8;
    private static final int SCALE_AMOUNT = 2;

    /**
     * Адаптивный расчет сумм для минимизации дисбаланса после lot size корректировки
     */
    public BigDecimal[] calculate(TradingProvider provider, Pair pair, BigDecimal totalAmount) {
        try {
            BigDecimal longPrice = provider.getCurrentPrice(pair.getLongTicker());
            BigDecimal shortPrice = provider.getCurrentPrice(pair.getShortTicker());

            if (isInvalidPrice(longPrice) || isInvalidPrice(shortPrice)) {
                log.warn("⚠️ Не удалось получить корректные цены для адаптивного расчета, используем равное распределение 50/50");
                return equalSplit(totalAmount);
            }

            BigDecimal bestLongAmount = BigDecimal.ZERO;
            BigDecimal bestShortAmount = BigDecimal.ZERO;
            BigDecimal minDifference = null;

            for (BigDecimal longPercent = PERCENT_START; longPercent.compareTo(PERCENT_END) <= 0; longPercent = longPercent.add(BigDecimal.ONE)) {
                BigDecimal longAmount = totalAmount.multiply(longPercent).divide(PERCENT_100, SCALE_AMOUNT, RoundingMode.HALF_UP);
                BigDecimal shortAmount = totalAmount.subtract(longAmount);

                BigDecimal adjustedLongAmount = adjustedAmount(longAmount, longPrice);
                BigDecimal adjustedShortAmount = adjustedAmount(shortAmount, shortPrice);

                BigDecimal difference = adjustedLongAmount.subtract(adjustedShortAmount).abs();

                if (minDifference == null || difference.compareTo(minDifference) < 0) {
                    minDifference = difference;
                    bestLongAmount = longAmount;
                    bestShortAmount = shortAmount;
                }
            }

            log.debug("🎯 Оптимальное распределение: LONG = {}, SHORT = {}, ожидаемая разность после корректировки: {}",
                    bestLongAmount, bestShortAmount, minDifference);

            return new BigDecimal[]{bestLongAmount, bestShortAmount};

        } catch (Exception e) {
            log.warn("⚠️ Ошибка при адаптивном расчете: {}. Возврат к равному распределению 50/50", e.getMessage());
            return equalSplit(totalAmount);
        }
    }

    private boolean isInvalidPrice(BigDecimal price) {
        return price == null || price.compareTo(BigDecimal.ZERO) <= 0;
    }

    private BigDecimal adjustedAmount(BigDecimal amount, BigDecimal price) {
        BigDecimal size = amount.divide(price, SCALE_PRICE, RoundingMode.HALF_UP);
        BigDecimal adjustedSize = size.setScale(0, RoundingMode.DOWN);
        return adjustedSize.multiply(price);
    }

    private BigDecimal[] equalSplit(BigDecimal totalAmount) {
        BigDecimal half = totalAmount.divide(BigDecimal.valueOf(2), SCALE_AMOUNT, RoundingMode.HALF_UP);
        return new BigDecimal[]{half, half};
    }
}
