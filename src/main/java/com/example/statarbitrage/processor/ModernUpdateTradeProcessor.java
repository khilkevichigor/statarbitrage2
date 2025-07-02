package com.example.statarbitrage.processor;

import com.example.statarbitrage.dto.TradingPair;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.TradeStatus;
import com.example.statarbitrage.service.ModernPairDataService;
import com.example.statarbitrage.service.ModernZScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Современный процессор для обновления активных трейдов
 * Использует TradingPair вместо ZScoreData
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModernUpdateTradeProcessor {
    
    private final ModernZScoreService zScoreService;
    private final ModernPairDataService pairDataService;

    /**
     * Обновление активного трейда
     */
    public boolean updateTrade(PairData pairData, Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🔄 Обновление трейда: {} / {}", 
            pairData.getLongTicker(), pairData.getShortTicker());
        
        try {
            // Получаем актуальные данные для этой пары
            TradingPair currentData = zScoreService.analyzeSpecificPair(
                pairData.getLongTicker(), 
                pairData.getShortTicker(), 
                candlesMap, 
                settings);
            
            if (currentData == null) {
                log.warn("⚠️ Не удалось получить актуальные данные для пары {} / {}", 
                    pairData.getLongTicker(), pairData.getShortTicker());
                return false;
            }
            
            // Сохраняем предыдущие значения для сравнения
            double previousZ = pairData.getZScoreCurrent();
            TradeStatus previousStatus = pairData.getStatus();
            
            // Обновляем данные
            pairDataService.updatePairData(pairData, currentData, candlesMap);
            
            // Анализируем изменения
            analyzeTradeChanges(pairData, currentData, previousZ, previousStatus);
            
            // Логируем обновление
            logTradeUpdate(pairData, currentData, previousZ);
            
            return true;
            
        } catch (Exception e) {
            log.error("❌ Ошибка обновления трейда {}: {}", 
                pairData.getLongTicker(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Массовое обновление списка активных трейдов
     */
    public int updateActiveTrades(List<PairData> activeTrades, Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🔄 Массовое обновление {} активных трейдов", activeTrades.size());
        
        int successCount = 0;
        
        for (PairData trade : activeTrades) {
            try {
                boolean updated = updateTrade(trade, settings, candlesMap);
                if (updated) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("❌ Ошибка обновления трейда {}: {}", trade.getLongTicker(), e.getMessage());
            }
        }
        
        log.info("✅ Обновлено {} из {} трейдов", successCount, activeTrades.size());
        return successCount;
    }

    /**
     * Проверка условий выхода из трейда
     */
    public boolean shouldExitTrade(PairData pairData, Settings settings) {
        // Проверяем достижение целевого Z-score (возврат к среднему)
        if (isZScoreNearZero(pairData, settings)) {
            log.info("🎯 Трейд {} готов к выходу: Z-score приблизился к нулю ({})", 
                pairData.getLongTicker(), String.format("%.2f", pairData.getZScoreCurrent()));
            return true;
        }
        
        // Проверяем максимальное время удержания позиции
        if (isMaxHoldingTimeReached(pairData, settings)) {
            log.info("⏰ Трейд {} готов к выходу: достигнуто максимальное время удержания", 
                pairData.getLongTicker());
            return true;
        }
        
        // Проверяем стоп-лосс условия
        if (isStopLossTriggered(pairData, settings)) {
            log.info("🛑 Трейд {} готов к выходу: сработал стоп-лосс", 
                pairData.getLongTicker());
            return true;
        }
        
        return false;
    }

    /**
     * Анализ изменений в трейде
     */
    private void analyzeTradeChanges(PairData pairData, TradingPair currentData, 
                                   double previousZ, TradeStatus previousStatus) {
        double currentZ = pairData.getZScoreCurrent();
        
        // Проверяем разворот тренда
        if (hasZScoreReversed(previousZ, currentZ)) {
            log.info("🔄 РАЗВОРОТ Z-SCORE в трейде {}: {} → {}", 
                pairData.getLongTicker(), String.format("%.2f", previousZ), String.format("%.2f", currentZ));
        }
        
        // Проверяем изменение статуса
        if (pairData.getStatus() != previousStatus) {
            log.info("📊 ИЗМЕНЕНИЕ СТАТУСА трейда {}: {} → {}", 
                pairData.getLongTicker(), previousStatus, pairData.getStatus());
        }
        
        // Проверяем силу сигнала
        double signalStrength = Math.abs(currentZ);
        if (signalStrength > 3.0) {
            log.warn("⚠️ СИЛЬНЫЙ СИГНАЛ в трейде {}: Z-score = {}, корреляция = {}", 
                pairData.getLongTicker(), String.format("%.2f", currentZ), 
                String.format("%.2f", currentData.getCorrelation() != null ? currentData.getCorrelation() : 0.0));
        }
    }

    /**
     * Логирование обновления трейда
     */
    private void logTradeUpdate(PairData pairData, TradingPair currentData, double previousZ) {
        log.info("📈 ОБНОВЛЕНИЕ ТРЕЙДА: {}", pairData.getLongTicker());
        log.info("   Z-Score: {} → {} (изменение: {})", 
            String.format("%.2f", previousZ), 
            String.format("%.2f", pairData.getZScoreCurrent()), 
            String.format("%.2f", pairData.getZScoreCurrent() - previousZ));
        log.info("   Статистика: {}", currentData.getStatisticsString());
        log.info("   Статус: {}", pairData.getStatus());
        
        // Расчет текущего P&L (если есть цены входа)
        if (pairData.getLongTickerEntryPrice() > 0 && pairData.getShortTickerEntryPrice() > 0) {
            double longPnl = ((pairData.getLongTickerCurrentPrice() - pairData.getLongTickerEntryPrice()) 
                            / pairData.getLongTickerEntryPrice()) * 100;
            double shortPnl = ((pairData.getShortTickerEntryPrice() - pairData.getShortTickerCurrentPrice()) 
                             / pairData.getShortTickerEntryPrice()) * 100;
            double totalPnl = (longPnl + shortPnl) / 2;
            
            log.info("   P&L: LONG={}%, SHORT={}%, TOTAL={}%", 
                String.format("%.2f", longPnl), String.format("%.2f", shortPnl), String.format("%.2f", totalPnl));
        }
    }

    /**
     * Проверка разворота Z-score
     */
    private boolean hasZScoreReversed(double previousZ, double currentZ) {
        return (previousZ > 0 && currentZ < 0) || (previousZ < 0 && currentZ > 0);
    }

    /**
     * Проверка приближения Z-score к нулю
     */
    private boolean isZScoreNearZero(PairData pairData, Settings settings) {
        double currentZ = Math.abs(pairData.getZScoreCurrent());
        double exitThreshold = settings.getExitZMin() > 0 ? settings.getExitZMin() : 0.5;
        return currentZ <= exitThreshold;
    }

    /**
     * Проверка максимального времени удержания
     */
    private boolean isMaxHoldingTimeReached(PairData pairData, Settings settings) {
        if (settings.getExitTimeHours() <= 0) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long entryTime = pairData.getTimestamp();
        long holdingHours = (currentTime - entryTime) / (1000 * 60 * 60);
        
        return holdingHours >= settings.getExitTimeHours();
    }

    /**
     * Проверка условий стоп-лосса
     */
    private boolean isStopLossTriggered(PairData pairData, Settings settings) {
        if (settings.getExitStop() <= 0 || pairData.getLongTickerEntryPrice() <= 0) {
            return false;
        }
        
        // Простой расчет P&L
        double longPnl = ((pairData.getLongTickerCurrentPrice() - pairData.getLongTickerEntryPrice()) 
                        / pairData.getLongTickerEntryPrice()) * 100;
        double shortPnl = ((pairData.getShortTickerEntryPrice() - pairData.getShortTickerCurrentPrice()) 
                         / pairData.getShortTickerEntryPrice()) * 100;
        double totalPnl = (longPnl + shortPnl) / 2;
        
        return totalPnl <= settings.getExitStop();
    }

    /**
     * Подготовка трейда к закрытию
     */
    public void prepareTradeForExit(PairData pairData, String reason) {
        log.info("🚪 Подготовка трейда {} к закрытию. Причина: {}", 
            pairData.getLongTicker(), reason);
        
        pairData.setStatus(TradeStatus.CLOSING);
        
        log.info("✅ Трейд {} подготовлен к закрытию", pairData.getLongTicker());
    }
}