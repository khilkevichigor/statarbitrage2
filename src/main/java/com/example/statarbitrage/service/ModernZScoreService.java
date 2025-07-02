package com.example.statarbitrage.service;

import com.example.statarbitrage.adapter.TradingPairAdapter;
import com.example.statarbitrage.dto.TradingPair;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Современная версия ZScoreService работающая с TradingPair
 * Заменяет старый ZScoreService с ZScoreData
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModernZScoreService {
    
    private final TradingPairAdapter adapter;

    /**
     * Получение топ N торговых пар
     * Аналог старого getTopNPairs(), но с TradingPair
     */
    public List<TradingPair> getTopNPairs(Settings settings, Map<String, List<Candle>> candlesMap, int count) {
        log.info("🔍 Поиск топ {} торговых пар", count);
        
        // Получаем все пары от Python API
        List<TradingPair> allPairs = adapter.fetchTradingPairs(settings, candlesMap);
        
        // Фильтруем по критериям
        List<TradingPair> validPairs = adapter.filterValidPairs(allPairs, settings);
        
        // Получаем топ N
        List<TradingPair> topPairs = adapter.getTopPairs(validPairs, count);
        
        // Логируем результаты
        log.info("✅ Найдено {} валидных пар из {} кандидатов, выбрано топ {}", 
            validPairs.size(), allPairs.size(), topPairs.size());
        
        adapter.logTradingPairs(topPairs);
        
        return topPairs;
    }

    /**
     * Расчет данных для одной пары (аналог calculateZScoreData)
     */
    public TradingPair calculateTradingPairData(Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🧮 Расчет данных для торговой пары");
        
        List<TradingPair> pairs = adapter.fetchTradingPairs(settings, candlesMap);
        
        if (pairs.isEmpty()) {
            log.warn("⚠️ Не найдено торговых пар для анализа");
            return null;
        }
        
        // Возвращаем лучшую пару по силе сигнала
        TradingPair best = pairs.stream()
                .filter(pair -> pair.isValidForTradingExtended(
                    settings.getMinCorrelation(),
                    settings.getMinPvalue(), 
                    settings.getMinZ(),
                    settings.getMinAdfValue()))
                .max((p1, p2) -> Double.compare(p1.getSignalStrength(), p2.getSignalStrength()))
                .orElse(null);
        
        if (best != null) {
            log.info("✅ Лучшая пара: {} с силой сигнала {:.2f}", 
                best.getDisplayName(), best.getSignalStrength());
        }
        
        return best;
    }

    /**
     * Расчет данных для нового трейда
     */
    public Optional<TradingPair> calculateTradingPairForNewTrade(Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🚀 Расчет данных для нового трейда");
        
        TradingPair pair = calculateTradingPairData(settings, candlesMap);
        
        if (pair == null) {
            log.warn("⚠️ Не найдено подходящих пар для нового трейда");
            return Optional.empty();
        }
        
        // Дополнительные проверки для нового трейда
        boolean suitable = pair.getSignalStrength() >= settings.getMinZ() && 
                          pair.getPValue() != null && pair.getPValue() <= settings.getMinPvalue();
        
        if (!suitable) {
            log.warn("⚠️ Найденная пара не подходит для нового трейда: {}", pair.getStatisticsString());
            return Optional.empty();
        }
        
        log.info("✅ Пара готова для нового трейда: {}", pair.getDisplayName());
        return Optional.of(pair);
    }

    /**
     * Получение лучшей пары по критериям
     */
    public TradingPair getBestPairByCriteria(List<TradingPair> pairs, Settings settings) {
        return pairs.stream()
                .filter(pair -> pair.isValidForTradingExtended(
                    settings.getMinCorrelation(),
                    settings.getMinPvalue(),
                    settings.getMinZ(), 
                    settings.getMinAdfValue()))
                .max((p1, p2) -> {
                    // Сравниваем по силе Z-score, затем по корреляции
                    int zCompare = Double.compare(p1.getSignalStrength(), p2.getSignalStrength());
                    if (zCompare != 0) return zCompare;
                    
                    double corr1 = p1.getCorrelation() != null ? Math.abs(p1.getCorrelation()) : 0.0;
                    double corr2 = p2.getCorrelation() != null ? Math.abs(p2.getCorrelation()) : 0.0;
                    return Double.compare(corr1, corr2);
                })
                .orElse(null);
    }

    /**
     * Детальный анализ конкретной пары
     */
    public TradingPair analyzeSpecificPair(String ticker1, String ticker2, 
                                          Map<String, List<Candle>> candlesMap, 
                                          Settings settings) {
        log.info("🔬 Детальный анализ пары: {} - {}", ticker1, ticker2);
        
        return adapter.analyzePairDetailed(ticker1, ticker2, candlesMap, settings);
    }

    /**
     * Проверка размера списка пар
     */
    public void validatePairsSize(List<TradingPair> pairs, int expectedSize) {
        if (pairs.size() < expectedSize) {
            String message = String.format("Недостаточно торговых пар: найдено %d, требуется %d", 
                pairs.size(), expectedSize);
            log.error("❌ {}", message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Проверка положительного Z-score
     */
    public void validatePositiveZScore(List<TradingPair> pairs) {
        boolean hasPositiveZ = pairs.stream()
                .anyMatch(pair -> pair.getZscore() != null && pair.getZscore() > 0);
        
        if (!hasPositiveZ) {
            log.error("❌ Не найдено пар с положительным Z-score");
            throw new IllegalStateException("Нет пар с положительным Z-score");
        }
    }
}