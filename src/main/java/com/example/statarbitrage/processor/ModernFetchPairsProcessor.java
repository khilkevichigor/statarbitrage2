package com.example.statarbitrage.processor;

import com.example.statarbitrage.dto.TradingPair;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.service.ModernPairDataService;
import com.example.statarbitrage.service.ModernZScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Современный процессор для получения торговых пар
 * Использует TradingPair вместо ZScoreData
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModernFetchPairsProcessor {
    
    private final ModernZScoreService zScoreService;
    private final ModernPairDataService pairDataService;

    /**
     * Получение торговых пар для UI
     */
    public List<PairData> fetchPairs(Settings settings, Map<String, List<Candle>> candlesMap, int count) {
        log.info("🔍 Получение {} торговых пар через современный процессор", count);
        
        try {
            // Получаем топ пары через новый API
            List<TradingPair> tradingPairs = zScoreService.getTopNPairs(settings, candlesMap, count);
            
            if (tradingPairs.isEmpty()) {
                log.warn("⚠️ Не найдено торговых пар, соответствующих критериям");
                return List.of();
            }
            
            // Логируем найденные пары
            log.info("📊 Найденные торговые пары:");
            for (int i = 0; i < tradingPairs.size(); i++) {
                TradingPair pair = tradingPairs.get(i);
                log.info("{}. {} → {} | {}", 
                    i + 1,
                    pair.getBuyTicker(),
                    pair.getSellTicker(),
                    pair.getStatisticsString());
            }
            
            // Конвертируем в PairData для UI
            List<PairData> pairDataList = pairDataService.createPairDataList(tradingPairs, candlesMap);
            
            log.info("✅ Создано {} объектов PairData для UI", pairDataList.size());
            return pairDataList;
            
        } catch (Exception e) {
            log.error("❌ Ошибка в процессоре получения пар: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка получения торговых пар", e);
        }
    }

    /**
     * Получение лучшей пары для автоматической торговли
     */
    public PairData fetchBestPair(Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🎯 Поиск лучшей торговой пары для автоматической торговли");
        
        TradingPair bestPair = zScoreService.calculateTradingPairData(settings, candlesMap);
        
        if (bestPair == null) {
            log.warn("⚠️ Не найдено подходящих пар для автоматической торговли");
            return null;
        }
        
        log.info("🏆 Лучшая пара: {} | {}", bestPair.getDisplayName(), bestPair.getStatisticsString());
        
        return pairDataService.createPairData(bestPair, candlesMap);
    }

    /**
     * Валидация и фильтрация торговых пар
     */
    public List<TradingPair> validateAndFilterPairs(List<TradingPair> pairs, Settings settings) {
        log.info("🔍 Валидация {} торговых пар", pairs.size());
        
        // Проверяем размер
        zScoreService.validatePairsSize(pairs, 1);
        
        // Проверяем наличие положительных Z-score
        zScoreService.validatePositiveZScore(pairs);
        
        // Фильтруем по расширенным критериям
        List<TradingPair> validPairs = pairs.stream()
                .filter(pair -> pair.isValidForTradingExtended(
                    settings.getMinCorrelation(),
                    settings.getMinPvalue(),
                    settings.getMinZ(),
                    settings.getMinAdfValue()))
                .toList();
        
        log.info("✅ Прошли валидацию {} пар из {}", validPairs.size(), pairs.size());
        return validPairs;
    }

    /**
     * Исключение уже торгующихся пар
     */
    public List<TradingPair> excludeActivePairs(List<TradingPair> newPairs, List<PairData> activePairs) {
        log.info("🚫 Исключение {} активных пар из {} новых", activePairs.size(), newPairs.size());
        
        return pairDataService.excludeExistingTradingPairs(newPairs, activePairs);
    }

    /**
     * Полный процесс получения пар с валидацией и исключениями
     */
    public List<PairData> fetchValidPairs(Settings settings, 
                                         Map<String, List<Candle>> candlesMap, 
                                         List<PairData> activePairs, 
                                         int count) {
        log.info("🔄 Полный процесс получения {} валидных торговых пар", count);
        
        try {
            // 1. Получаем все возможные пары
            List<TradingPair> allPairs = zScoreService.getTopNPairs(settings, candlesMap, count * 2); // Берем больше для фильтрации
            
            // 2. Валидируем
            List<TradingPair> validPairs = validateAndFilterPairs(allPairs, settings);
            
            // 3. Исключаем активные
            List<TradingPair> newPairs = excludeActivePairs(validPairs, activePairs);
            
            // 4. Ограничиваем количество
            List<TradingPair> limitedPairs = newPairs.stream().limit(count).toList();
            
            // 5. Конвертируем в PairData
            List<PairData> result = pairDataService.createPairDataList(limitedPairs, candlesMap);
            
            log.info("✅ Процесс завершен: получено {} финальных пар", result.size());
            return result;
            
        } catch (Exception e) {
            log.error("❌ Ошибка в полном процессе получения пар: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка процесса получения пар", e);
        }
    }
}