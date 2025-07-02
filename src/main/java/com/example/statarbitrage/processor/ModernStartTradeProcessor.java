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
import java.util.Optional;

/**
 * Современный процессор для запуска новых трейдов
 * Использует TradingPair вместо ZScoreData
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModernStartTradeProcessor {
    
    private final ModernZScoreService zScoreService;
    private final ModernPairDataService pairDataService;

    /**
     * Запуск нового трейда для выбранной пары
     */
    public boolean startNewTrade(PairData pairData, Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🚀 Запуск нового трейда для пары: {} / {}", 
            pairData.getLongTicker(), pairData.getShortTicker());
        
        try {
            // Получаем актуальные данные для этой пары
            Optional<TradingPair> tradingPairOpt = zScoreService.calculateTradingPairForNewTrade(settings, candlesMap);
            
            if (tradingPairOpt.isEmpty()) {
                log.warn("⚠️ Не удалось получить данные для нового трейда");
                return false;
            }
            
            TradingPair tradingPair = tradingPairOpt.get();
            
            // Проверяем, что это та же пара
            if (!isSamePair(pairData, tradingPair)) {
                log.warn("⚠️ Найденная пара {} не соответствует запрошенной {} / {}", 
                    tradingPair.getDisplayName(), pairData.getLongTicker(), pairData.getShortTicker());
                return false;
            }
            
            // Проверяем критерии для входа в трейд
            if (!isValidForNewTrade(tradingPair, settings)) {
                log.warn("⚠️ Пара {} не соответствует критериям для нового трейда: {}", 
                    tradingPair.getDisplayName(), tradingPair.getStatisticsString());
                return false;
            }
            
            // Обновляем PairData новыми данными
            pairDataService.updatePairData(pairData, tradingPair, candlesMap);
            
            // Устанавливаем статус TRADING и точки входа
            startTradingProcess(pairData, candlesMap);
            
            // Логируем успешный старт
            logSuccessfulTradeStart(pairData, tradingPair);
            
            return true;
            
        } catch (Exception e) {
            log.error("❌ Ошибка запуска нового трейда для {}: {}", 
                pairData.getLongTicker(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Автоматический поиск и запуск лучшего трейда
     */
    public Optional<PairData> startBestAvailableTrade(Settings settings, 
                                                     Map<String, List<Candle>> candlesMap,
                                                     List<PairData> activePairs) {
        log.info("🎯 Автоматический поиск лучшего трейда");
        
        try {
            // Получаем лучшую доступную пару
            Optional<TradingPair> bestPairOpt = zScoreService.calculateTradingPairForNewTrade(settings, candlesMap);
            
            if (bestPairOpt.isEmpty()) {
                log.info("ℹ️ Нет подходящих пар для автоматического трейда");
                return Optional.empty();
            }
            
            TradingPair bestPair = bestPairOpt.get();
            
            // Проверяем, что пара не торгуется уже
            boolean alreadyTrading = activePairs.stream()
                    .anyMatch(active -> isSamePair(active, bestPair));
            
            if (alreadyTrading) {
                log.info("ℹ️ Лучшая пара {} уже торгуется", bestPair.getDisplayName());
                return Optional.empty();
            }
            
            // Создаем новый PairData и запускаем трейд
            PairData newPairData = pairDataService.createPairData(bestPair, candlesMap);
            
            boolean started = startNewTrade(newPairData, settings, candlesMap);
            
            if (started) {
                log.info("🎉 Автоматически запущен трейд: {}", newPairData.getLongTicker());
                return Optional.of(newPairData);
            } else {
                log.warn("⚠️ Не удалось автоматически запустить трейд для {}", bestPair.getDisplayName());
                return Optional.empty();
            }
            
        } catch (Exception e) {
            log.error("❌ Ошибка автоматического запуска трейда: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Валидация пары для нового трейда
     */
    private boolean isValidForNewTrade(TradingPair pair, Settings settings) {
        // Основные критерии
        if (!pair.isValidForTradingExtended(
                settings.getMinCorrelation(), 
                settings.getMinPvalue(), 
                settings.getMinZ(),
                settings.getMinAdfValue())) {
            return false;
        }
        
        // Дополнительные критерии для нового трейда
        double signalStrength = pair.getSignalStrength();
        if (signalStrength < settings.getMinZ() * 1.2) { // Требуем 20% превышение минимального Z
            log.debug("Signal strength {:.2f} below threshold {:.2f}", 
                signalStrength, settings.getMinZ() * 1.2);
            return false;
        }
        
        return true;
    }

    /**
     * Запуск процесса торговли
     */
    private void startTradingProcess(PairData pairData, Map<String, List<Candle>> candlesMap) {
        // Устанавливаем цены входа из текущих цен
        double longPrice = pairData.getLongTickerCurrentPrice();
        double shortPrice = pairData.getShortTickerCurrentPrice();
        
        pairData.setLongTickerEntryPrice(longPrice);
        pairData.setShortTickerEntryPrice(shortPrice);
        pairData.setZScoreEntry(pairData.getZScoreCurrent());
        pairData.setStatus(TradeStatus.TRADING);
        
        log.info("💹 Точки входа установлены: LONG {} = ${:.2f}, SHORT {} = ${:.2f}, Z = {:.2f}",
            pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
            pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
            pairData.getZScoreEntry());
    }

    /**
     * Логирование успешного запуска трейда
     */
    private void logSuccessfulTradeStart(PairData pairData, TradingPair tradingPair) {
        log.info("🎉 НОВЫЙ ТРЕЙД ЗАПУЩЕН:");
        log.info("   Пара: {} → {}", 
            tradingPair.getBuyTicker(), tradingPair.getSellTicker());
        log.info("   Статистика: {}", tradingPair.getStatisticsString());
        log.info("   Цены входа: LONG=${:.2f}, SHORT=${:.2f}", 
            pairData.getLongTickerEntryPrice(), pairData.getShortTickerEntryPrice());
        log.info("   Z-Score входа: {:.2f}", pairData.getZScoreEntry());
        log.info("   Направление: {}", tradingPair.getTradeDirection());
    }

    /**
     * Проверка, что PairData и TradingPair представляют одну пару
     */
    private boolean isSamePair(PairData pairData, TradingPair tradingPair) {
        return (pairData.getLongTicker().equals(tradingPair.getBuyTicker()) &&
                pairData.getShortTicker().equals(tradingPair.getSellTicker())) ||
               (pairData.getLongTicker().equals(tradingPair.getSellTicker()) &&
                pairData.getShortTicker().equals(tradingPair.getBuyTicker()));
    }

    /**
     * Подготовка пары к торговле
     */
    public void preparePairForTrading(PairData pairData, Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🔧 Подготовка пары {} к торговле", pairData.getLongTicker());
        
        // Обновляем текущие цены
        pairDataService.updatePairData(pairData, 
            pairDataService.convertToTradingPair(pairData), candlesMap);
        
        // Устанавливаем статус SELECTED
        pairData.setStatus(TradeStatus.SELECTED);
        
        log.info("✅ Пара {} подготовлена к торговле", pairData.getLongTicker());
    }
}