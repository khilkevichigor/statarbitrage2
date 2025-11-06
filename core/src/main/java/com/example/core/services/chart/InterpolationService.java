package com.example.core.services.chart;

import com.example.shared.dto.Candle;
import com.example.shared.dto.CorrelationHistoryItem;
import com.example.shared.dto.PixelSpreadHistoryItem;
import com.example.shared.dto.ProfitHistoryItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 🎯 Сервис для интерполяции данных на точные временные метки Z-Score
 * Обеспечивает идеальную синхронизацию всех графиков
 */
@Slf4j
@Service
public class InterpolationService {

    /**
     * 🎯 Интерполирует цену свечи на точный таймштамп Z-Score
     * Использует линейную интерполяцию между ближайшими свечами
     */
    public Double interpolatePrice(List<Candle> candles, long targetTimestamp) {
        if (candles == null || candles.isEmpty()) {
            log.trace("🎯 Нет свечей для интерполяции цены на {}", new Date(targetTimestamp));
            return null;
        }

        // Ищем ближайшие свечи до и после целевого времени
        Candle beforeCandle = null;
        Candle afterCandle = null;

        for (Candle candle : candles) {
            if (candle.getTimestamp() <= targetTimestamp) {
                if (beforeCandle == null || candle.getTimestamp() > beforeCandle.getTimestamp()) {
                    beforeCandle = candle;
                }
            }
            if (candle.getTimestamp() >= targetTimestamp) {
                if (afterCandle == null || candle.getTimestamp() < afterCandle.getTimestamp()) {
                    afterCandle = candle;
                }
            }
        }

        // Если точное совпадение
        if (beforeCandle != null && beforeCandle.getTimestamp() == targetTimestamp) {
            log.trace("🎯 Точное совпадение свечи: {}", beforeCandle.getClose());
            return beforeCandle.getClose();
        }
        if (afterCandle != null && afterCandle.getTimestamp() == targetTimestamp) {
            log.trace("🎯 Точное совпадение свечи: {}", afterCandle.getClose());
            return afterCandle.getClose();
        }

        // Линейная интерполяция между двумя свечами
        if (beforeCandle != null && afterCandle != null && !beforeCandle.equals(afterCandle)) {
            long timeDiff = afterCandle.getTimestamp() - beforeCandle.getTimestamp();
            double priceDiff = afterCandle.getClose() - beforeCandle.getClose();
            long targetDiff = targetTimestamp - beforeCandle.getTimestamp();

            double interpolatedPrice = beforeCandle.getClose() + (priceDiff * targetDiff / (double) timeDiff);

            log.trace("🎯 Интерполяция цены: {} -> {} (между {} и {})",
                    new Date(targetTimestamp), interpolatedPrice, beforeCandle.getClose(), afterCandle.getClose());

            return interpolatedPrice;
        }

        // Fallback: ближайшая доступная цена
        if (beforeCandle != null) {
            log.trace("🎯 Используем цену предыдущей свечи: {}", beforeCandle.getClose());
            return beforeCandle.getClose();
        }
        if (afterCandle != null) {
            log.trace("🎯 Используем цену следующей свечи: {}", afterCandle.getClose());
            return afterCandle.getClose();
        }

        log.warn("⚠️ Не удалось интерполировать цену для {}", new Date(targetTimestamp));
        return null;
    }

    /**
     * 🎯 Интерполирует профит на точный таймштамп Z-Score
     */
    public Double interpolateProfit(List<ProfitHistoryItem> profitHistory, long targetTimestamp) {
        if (profitHistory == null || profitHistory.isEmpty()) {
            log.trace("🎯 Нет данных профита для интерполяции на {}", new Date(targetTimestamp));
            return null;
        }

        // Ищем ближайшие записи до и после целевого времени
        ProfitHistoryItem beforeItem = null;
        ProfitHistoryItem afterItem = null;

        for (ProfitHistoryItem item : profitHistory) {
            if (item.getTimestamp() <= targetTimestamp) {
                if (beforeItem == null || item.getTimestamp() > beforeItem.getTimestamp()) {
                    beforeItem = item;
                }
            }
            if (item.getTimestamp() >= targetTimestamp) {
                if (afterItem == null || item.getTimestamp() < afterItem.getTimestamp()) {
                    afterItem = item;
                }
            }
        }

        // Если точное совпадение
        if (beforeItem != null && beforeItem.getTimestamp() == targetTimestamp) {
            log.trace("🎯 Точное совпадение профита: {}%", beforeItem.getProfitPercent());
            return beforeItem.getProfitPercent();
        }
        if (afterItem != null && afterItem.getTimestamp() == targetTimestamp) {
            log.trace("🎯 Точное совпадение профита: {}%", afterItem.getProfitPercent());
            return afterItem.getProfitPercent();
        }

        // Линейная интерполяция между двумя записями
        if (beforeItem != null && afterItem != null && !beforeItem.equals(afterItem)) {
            long timeDiff = afterItem.getTimestamp() - beforeItem.getTimestamp();
            double profitDiff = afterItem.getProfitPercent() - beforeItem.getProfitPercent();
            long targetDiff = targetTimestamp - beforeItem.getTimestamp();

            double interpolatedProfit = beforeItem.getProfitPercent() + (profitDiff * targetDiff / (double) timeDiff);

            log.trace("🎯 Интерполяция профита: {} -> {}% (между {}% и {}%)",
                    new Date(targetTimestamp), interpolatedProfit, beforeItem.getProfitPercent(), afterItem.getProfitPercent());

            return interpolatedProfit;
        }

        // Fallback: ближайшее доступное значение
        if (beforeItem != null) {
            log.trace("🎯 Используем предыдущий профит: {}%", beforeItem.getProfitPercent());
            return beforeItem.getProfitPercent();
        }
        if (afterItem != null) {
            log.trace("🎯 Используем следующий профит: {}%", afterItem.getProfitPercent());
            return afterItem.getProfitPercent();
        }

        log.warn("⚠️ Не удалось интерполировать профит для {}", new Date(targetTimestamp));
        return null;
    }

    /**
     * 🎯 Интерполирует корреляцию на точный таймштамп Z-Score
     */
    public Double interpolateCorrelation(List<CorrelationHistoryItem> correlationHistory, long targetTimestamp) {
        if (correlationHistory == null || correlationHistory.isEmpty()) {
            log.trace("🎯 Нет данных корреляции для интерполяции на {}", new Date(targetTimestamp));
            return null;
        }

        // Ищем ближайшие записи до и после целевого времени
        CorrelationHistoryItem beforeItem = null;
        CorrelationHistoryItem afterItem = null;

        for (CorrelationHistoryItem item : correlationHistory) {
            if (item.getTimestamp() <= targetTimestamp) {
                if (beforeItem == null || item.getTimestamp() > beforeItem.getTimestamp()) {
                    beforeItem = item;
                }
            }
            if (item.getTimestamp() >= targetTimestamp) {
                if (afterItem == null || item.getTimestamp() < afterItem.getTimestamp()) {
                    afterItem = item;
                }
            }
        }

        // Если точное совпадение
        if (beforeItem != null && beforeItem.getTimestamp() == targetTimestamp) {
            log.trace("🎯 Точное совпадение корреляции: {}", beforeItem.getCorrelation());
            return beforeItem.getCorrelation();
        }
        if (afterItem != null && afterItem.getTimestamp() == targetTimestamp) {
            log.trace("🎯 Точное совпадение корреляции: {}", afterItem.getCorrelation());
            return afterItem.getCorrelation();
        }

        // Линейная интерполяция между двумя записями
        if (beforeItem != null && afterItem != null && !beforeItem.equals(afterItem)) {
            long timeDiff = afterItem.getTimestamp() - beforeItem.getTimestamp();
            double correlationDiff = afterItem.getCorrelation() - beforeItem.getCorrelation();
            long targetDiff = targetTimestamp - beforeItem.getTimestamp();

            double interpolatedCorrelation = beforeItem.getCorrelation() + (correlationDiff * targetDiff / (double) timeDiff);

            log.trace("🎯 Интерполяция корреляции: {} -> {} (между {} и {})",
                    new Date(targetTimestamp), interpolatedCorrelation, beforeItem.getCorrelation(), afterItem.getCorrelation());

            return interpolatedCorrelation;
        }

        // Fallback: ближайшее доступное значение
        if (beforeItem != null) {
            log.trace("🎯 Используем предыдущую корреляцию: {}", beforeItem.getCorrelation());
            return beforeItem.getCorrelation();
        }
        if (afterItem != null) {
            log.trace("🎯 Используем следующую корреляцию: {}", afterItem.getCorrelation());
            return afterItem.getCorrelation();
        }

        log.warn("⚠️ Не удалось интерполировать корреляцию для {}", new Date(targetTimestamp));
        return null;
    }
}