package com.example.statarbitrage.service;

import com.example.statarbitrage.client.PythonRestClient;
import com.example.statarbitrage.dto.TradingPair;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для работы с торговыми парами
 * Использует упрощенную архитектрую с TradingPair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingPairService {
    
    @Qualifier("modernPythonRestClient")
    private final PythonRestClient pythonRestClient;
    
    /**
     * Поиск валидных торговых пар
     */
    public List<TradingPair> findValidTradingPairs(Map<String, List<Candle>> candlesMap, Settings settings) {
        log.info("🔍 Поиск торговых пар для {} тикеров", candlesMap.size());
        
        // Получаем пары из Python API
        List<TradingPair> allPairs = pythonRestClient.discoverTradingPairs(settings, candlesMap);
        
        // Фильтруем по настройкам
        List<TradingPair> validPairs = allPairs.stream()
                .filter(pair -> pair.isValidForTrading(
                    settings.getMinCorrelation(),
                    settings.getMinPvalue(),
                    settings.getMinZ()
                ))
                .collect(Collectors.toList());
        
        log.info("✅ Найдено {} валидных пар из {} кандидатов", validPairs.size(), allPairs.size());
        
        return validPairs;
    }
    
    /**
     * Сортировка пар по силе сигнала
     */
    public List<TradingPair> sortBySignalStrength(List<TradingPair> pairs) {
        return pairs.stream()
                .sorted((p1, p2) -> Double.compare(p2.getSignalStrength(), p1.getSignalStrength()))
                .collect(Collectors.toList());
    }
    
    /**
     * Получение топ N пар для торговли
     */
    public List<TradingPair> getTopTradingPairs(Map<String, List<Candle>> candlesMap, 
                                               Settings settings, 
                                               int maxPairs) {
        List<TradingPair> validPairs = findValidTradingPairs(candlesMap, settings);
        List<TradingPair> sortedPairs = sortBySignalStrength(validPairs);
        
        return sortedPairs.stream()
                .limit(maxPairs)
                .collect(Collectors.toList());
    }
    
    /**
     * Детальный анализ конкретной пары
     */
    public TradingPair analyzeSpecificPair(String ticker1, String ticker2,
                                          Map<String, List<Candle>> candlesMap,
                                          Settings settings) {
        log.info("🔬 Анализ пары: {} / {}", ticker1, ticker2);
        
        return pythonRestClient.analyzePairDetailed(ticker1, ticker2, candlesMap, settings);
    }
    
    /**
     * Логирование торговых решений
     */
    public void logTradingDecision(TradingPair pair) {
        log.info("💹 ТОРГОВОЕ РЕШЕНИЕ: КУПИТЬ {} | ПРОДАТЬ {} | Z-Score: {:.2f} | Корреляция: {:.2f}",
                pair.getBuyTicker(),
                pair.getSellTicker(),
                pair.getZscore(),
                pair.getCorrelation());
    }
    
    /**
     * Проверка изменения направления тренда
     */
    public boolean hasSignalReversed(TradingPair oldPair, TradingPair newPair) {
        if (oldPair.getZscore() == null || newPair.getZscore() == null) {
            return false;
        }
        
        // Проверяем смену знака Z-score
        return (oldPair.getZscore() > 0) != (newPair.getZscore() > 0);
    }
}