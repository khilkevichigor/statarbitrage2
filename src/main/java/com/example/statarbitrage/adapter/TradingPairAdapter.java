package com.example.statarbitrage.adapter;

import com.example.statarbitrage.client.PythonRestClient;
import com.example.statarbitrage.dto.TradingPair;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Адаптер для постепенной миграции с ZScoreData на TradingPair
 * Работает с ОБОИМИ сервисами - старым и новым Python API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingPairAdapter {
    
    @Qualifier("modernPythonRestClient")
    private final PythonRestClient newPythonClient;

    /**
     * Основной метод для получения торговых пар
     * Использует новый Python API с TradingPair
     */
    public List<TradingPair> fetchTradingPairs(Settings settings, Map<String, List<Candle>> candlesMap) {
        log.info("🔄 Получение торговых пар через новый API...");
        
        try {
            // Используем новый клиент с TradingPair
            List<TradingPair> pairs = newPythonClient.discoverTradingPairs(settings, candlesMap);
            
            log.info("✅ Получено {} торговых пар через новый API", pairs.size());
            return pairs;
            
        } catch (Exception e) {
            log.error("❌ Ошибка получения данных через новый API: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch trading pairs", e);
        }
    }

    /**
     * Детальный анализ конкретной пары
     */
    public TradingPair analyzePairDetailed(String ticker1, String ticker2, 
                                          Map<String, List<Candle>> candlesMap, 
                                          Settings settings) {
        log.info("🔬 Детальный анализ пары: {} / {}", ticker1, ticker2);
        
        return newPythonClient.analyzePairDetailed(ticker1, ticker2, candlesMap, settings);
    }

    /**
     * Фильтрация пар по критериям Settings
     */
    public List<TradingPair> filterValidPairs(List<TradingPair> pairs, Settings settings) {
        return pairs.stream()
                .filter(pair -> pair.isValidForTradingExtended(
                    settings.getMinCorrelation(),
                    settings.getMinPvalue(),
                    settings.getMinZ(),
                    settings.getMinAdfValue()
                ))
                .toList();
    }

    /**
     * Получение топ N пар по силе сигнала
     */
    public List<TradingPair> getTopPairs(List<TradingPair> pairs, int count) {
        return pairs.stream()
                .sorted((p1, p2) -> Double.compare(p2.getSignalStrength(), p1.getSignalStrength()))
                .limit(count)
                .toList();
    }

    /**
     * Логирование информации о торговых парах
     */
    public void logTradingPairs(List<TradingPair> pairs) {
        log.info("📊 Найденные торговые пары:");
        for (int i = 0; i < pairs.size(); i++) {
            TradingPair pair = pairs.get(i);
            log.info("{}. {} | {}", 
                i + 1, 
                pair.getDisplayName(), 
                pair.getStatisticsString());
        }
    }
}