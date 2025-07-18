package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.model.ArbitragePairTradeInfo;
import com.example.statarbitrage.trading.model.Position;
import com.example.statarbitrage.trading.model.PositionVerificationResult;
import com.example.statarbitrage.trading.model.TradeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Сервис для унифицированного обновления профита торговых пар
 * Устраняет дублирование кода и обеспечивает единый подход к расчету профита
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitUpdateService {

    /**
     * Обновляет профит на основе результатов закрытия позиций
     * Используется для реальной торговли при закрытии позиций
     */
    public void updateProfitFromTradeResults(PairData pairData, ArbitragePairTradeInfo tradeInfo) {
        try {
            TradeResult longResult = tradeInfo.getLongTradeResult();
            TradeResult shortResult = tradeInfo.getShortTradeResult();

            if (longResult == null || shortResult == null) {
                log.warn("⚠️ Не удалось получить результаты закрытия для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return;
            }

            // Обновляем текущие цены на основе фактических цен исполнения
            pairData.setLongTickerCurrentPrice(longResult.getExecutionPrice().doubleValue());
            pairData.setShortTickerCurrentPrice(shortResult.getExecutionPrice().doubleValue());

            // Рассчитываем чистый профит
            BigDecimal totalPnL = longResult.getPnl().add(shortResult.getPnl());
            BigDecimal totalFees = longResult.getFees().add(shortResult.getFees());
            BigDecimal netPnL = totalPnL.subtract(totalFees);

            // Конвертируем в процент от позиции
            BigDecimal profitPercent = calculateProfitPercent(
                    netPnL,
                    pairData.getLongTickerEntryPrice(),
                    pairData.getShortTickerEntryPrice()
            );

            pairData.setProfitChanges(profitPercent);

            log.info("🏦 Обновлен профит из результатов закрытия {}/{}: {}% (PnL: {}, комиссии: {})",
                    pairData.getLongTicker(), pairData.getShortTicker(),
                    profitPercent, totalPnL, totalFees);

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении профита из результатов закрытия для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Обновляет профит на основе открытых позиций
     * Используется для мониторинга текущего профита активных позиций
     */
    public void updateProfitFromOpenPositions(PairData pairData, PositionVerificationResult positionInfo) {
        try {
            Position longPosition = positionInfo.getLongPosition();
            Position shortPosition = positionInfo.getShortPosition();

            if (longPosition == null || shortPosition == null) {
                log.warn("⚠️ Не удалось получить информацию о позициях для пары {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
                return;
            }

            // Обновляем текущие цены
            pairData.setLongTickerCurrentPrice(longPosition.getCurrentPrice().doubleValue());
            pairData.setShortTickerCurrentPrice(shortPosition.getCurrentPrice().doubleValue());

            // Рассчитываем текущий нереализованный профит
            BigDecimal totalPnL = longPosition.getUnrealizedPnL().add(shortPosition.getUnrealizedPnL());
            BigDecimal totalFees = longPosition.getOpeningFees().add(shortPosition.getOpeningFees());
            BigDecimal netPnL = totalPnL.subtract(totalFees);

            // Конвертируем в процент от позиции
            BigDecimal profitPercent = calculateProfitPercent(
                    netPnL,
                    pairData.getLongTickerEntryPrice(),
                    pairData.getShortTickerEntryPrice()
            );

            pairData.setProfitChanges(profitPercent);

            log.info("📊 Обновлен профит из открытых позиций {}/{}: {}% (PnL: {}, комиссии: {})",
                    pairData.getLongTicker(), pairData.getShortTicker(),
                    profitPercent, totalPnL, totalFees);

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении профита из открытых позиций для пары {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }
    }

    /**
     * Рассчитывает процент профита от средней входной цены
     * Единая логика расчета для всех типов операций
     */
    private BigDecimal calculateProfitPercent(BigDecimal netPnL, double longEntryPrice, double shortEntryPrice) {
        try {
            BigDecimal longEntry = BigDecimal.valueOf(longEntryPrice);
            BigDecimal shortEntry = BigDecimal.valueOf(shortEntryPrice);
            BigDecimal avgEntryPrice = longEntry.add(shortEntry).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);

            if (avgEntryPrice.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("⚠️ Средняя входная цена меньше или равна нулю: {}", avgEntryPrice);
                return BigDecimal.ZERO;
            }

            return netPnL.divide(avgEntryPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

        } catch (Exception e) {
            log.error("❌ Ошибка при расчете процента профита: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}