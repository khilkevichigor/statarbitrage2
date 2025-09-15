package com.example.candles.service;

import com.example.candles.client.OkxFeignClient;
import com.example.candles.service.CandleCacheService;
import com.example.shared.dto.Candle;
import com.example.shared.models.Settings;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandlesService {

    private final OkxFeignClient okxFeignClient;
    private final CandleCacheService candleCacheService;
    
    @Value("${app.candle-cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${app.candle-cache.default-exchange:OKX}")
    private String defaultExchange;

    public Map<String, List<Candle>> getApplicableCandlesMap(Pair tradingPair, Settings settings) {
        List<String> tickers = List.of(tradingPair.getTickerA(), tradingPair.getTickerB());

        // Используем расширенный метод с пагинацией если требуется больше 300 свечей
        Map<String, List<Candle>> candlesMap;
        if (settings.getCandleLimit() > 300) {
            log.info("🔄 Используем пагинацию для получения {} свечей для пары {}",
                    (int) settings.getCandleLimit(), tradingPair.getPairName());
            candlesMap = getCandlesExtended(settings, tickers, (int) settings.getCandleLimit());
        } else {
            candlesMap = getCandles(settings, tickers, false);
        }

        validateCandlesLimitAndThrow(candlesMap, settings);
        return candlesMap;
    }

    //todo сделать умнее - через кэш или бд - зачем каждую минуту это делать! если объем есть то можно целый день работать, ну или чекать 1раз/час
    public Map<String, List<Candle>> getApplicableCandlesMap(Settings settings, List<String> tradingTickers) {
        List<String> applicableTickers = getApplicableTickers(settings, tradingTickers, "1D", true);

        // Используем расширенный метод с пагинацией если требуется больше 300 свечей
        Map<String, List<Candle>> candlesMap;
        if (settings.getCandleLimit() > 300) {
            log.info("🔄 Используем пагинацию для получения {} свечей для {} тикеров",
                    (int) settings.getCandleLimit(), applicableTickers.size());
            candlesMap = getCandlesExtended(settings, applicableTickers, (int) settings.getCandleLimit());
        } else {
            candlesMap = getCandles(settings, applicableTickers, true);
        }

        validateCandlesLimitAndThrow(candlesMap, settings);
        return candlesMap;
    }

    public Map<String, List<Candle>> getCandles(Settings settings, List<String> swapTickers, boolean isSorted) {
        try {
            log.debug("📡 Запрос к OKX для {} тикеров (таймфрейм: {}, лимит: {}, сортировка: {})",
                    swapTickers.size(), settings.getTimeframe(), (int) settings.getCandleLimit(), isSorted);

            Map<String, List<Candle>> result = okxFeignClient.getCandlesMap(swapTickers, settings.getTimeframe(), (int) settings.getCandleLimit(), isSorted);

            log.debug("📈 Получен ответ от OKX: {} тикеров с данными", result.size());

            return result;
        } catch (Exception e) {
            log.error("❌ Ошибка при запросе к OKX сервису: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    private List<String> getApplicableTickers(Settings settings, List<String> tradingTickers, String timeFrame, boolean isSorted) {
        List<String> swapTickers = okxFeignClient.getAllSwapTickers(isSorted);
        List<String> filteredTickers = swapTickers.stream()
                .filter(ticker -> !tradingTickers.contains(ticker))
                .toList();
        double minVolume = settings.isUseMinVolumeFilter() ? settings.getMinVolume() * 1_000_000 : 0.0;
        return okxFeignClient.getValidTickers(filteredTickers, timeFrame, (int) settings.getCandleLimit(), minVolume, isSorted);
    }

    /**
     * Расширенный метод для получения большого количества свечей с умным кэшированием
     * ПРИОРИТЕТ КЭША: Сначала пытается получить данные из кэша, при необходимости дополняет из API
     */
    public Map<String, List<Candle>> getCandlesExtended(Settings settings, List<String> swapTickers, int totalLimit) {
        long requestStartTime = System.currentTimeMillis();
        
        log.info("🎯 АК-47 ЗАПРОС: {} свечей, {} тикеров, таймфрейм '{}', кэш={}", 
                totalLimit, swapTickers.size(), settings.getTimeframe(), cacheEnabled ? "ВКЛ" : "ВЫКЛ");

        Map<String, List<Candle>> result = new HashMap<>();
        
        try {
            if (cacheEnabled) {
                log.info("💾 КЭШИРОВАНИЕ: Получаем свечи из кэша для {} тикеров", swapTickers.size());
                
                // Получаем данные из кэша (с автоматической догрузкой недостающих)
                result = candleCacheService.getCachedCandles(swapTickers, settings.getTimeframe(), 
                        totalLimit, defaultExchange);
                
                // Подробная статистика по кэшу
                long cacheHits = result.size();
                int totalCandlesFromCache = result.values().stream().mapToInt(List::size).sum();
                
                log.info("🎯 КЭША РЕЗУЛЬТАТ: {} из {} тикеров найдено в кэше, всего свечей: {}", 
                        cacheHits, swapTickers.size(), totalCandlesFromCache);
                
                // Проверяем качество данных из кэша
                validateCacheResults(result, totalLimit, settings.getTimeframe());
                
            } else {
                log.info("⚠️ КЭШИРОВАНИЕ ОТКЛЮЧЕНО: Используем прямое обращение к API");
                result = getCandlesDirectFromAPI(settings, swapTickers, totalLimit);
            }
            
            // Финальная валидация и логирование
            long requestDuration = System.currentTimeMillis() - requestStartTime;
            logFinalResults(result, totalLimit, requestDuration, cacheEnabled);
            
        } catch (Exception e) {
            log.error("💥 КРИТИЧЕСКАЯ ОШИБКА в getCandlesExtended: {}", e.getMessage(), e);
            
            // FALLBACK: Пытаемся получить хотя бы базовые данные
            log.warn("🛡️ АВАРИЙНЫЙ РЕЖИМ: Fallback к прямому API без кэша");
            result = getCandlesDirectFromAPI(settings, swapTickers, Math.min(300, totalLimit));
        }
        
        return result;
    }
    
    /**
     * Получение свечей напрямую из API (fallback метод)
     */
    private Map<String, List<Candle>> getCandlesDirectFromAPI(Settings settings, List<String> swapTickers, int totalLimit) {
        log.info("📡 ПРЯМОЙ API ЗАПРОС: {} свечей для {} тикеров", totalLimit, swapTickers.size());
        
        if (totalLimit <= 300) {
            return getCandles(settings, swapTickers, true);
        }
        
        // Для больших лимитов - старая логика пагинации
        return getCandlesWithPagination(settings, swapTickers, totalLimit);
    }
    
    /**
     * Валидация результатов кэша
     */
    private void validateCacheResults(Map<String, List<Candle>> cacheResults, int expectedCandleCount, String timeframe) {
        if (cacheResults.isEmpty()) {
            log.warn("⚠️ КЭША ПУСТОЙ: Не получено ни одного тикера из кэша");
            return;
        }
        
        // Проверяем качество данных
        int tickersWithGoodData = 0;
        int tickersWithPartialData = 0;
        int tickersWithBadData = 0;
        
        for (Map.Entry<String, List<Candle>> entry : cacheResults.entrySet()) {
            String ticker = entry.getKey();
            List<Candle> candles = entry.getValue();
            
            double completeness = (double) candles.size() / expectedCandleCount;
            
            if (completeness >= 0.95) {
                tickersWithGoodData++;
                log.debug("✅ {}: отличные данные ({}% = {}/{})", ticker, 
                        String.format("%.1f", completeness * 100), candles.size(), expectedCandleCount);
            } else if (completeness >= 0.80) {
                tickersWithPartialData++;
                log.debug("⚠️ {}: частичные данные ({}% = {}/{})", ticker, 
                        String.format("%.1f", completeness * 100), candles.size(), expectedCandleCount);
            } else {
                tickersWithBadData++;
                log.warn("❌ {}: плохие данные ({}% = {}/{})", ticker, 
                        String.format("%.1f", completeness * 100), candles.size(), expectedCandleCount);
            }
        }
        
        log.info("📊 КАЧЕСТВО КЭША: ✅{}(отлично) ⚠️{}(частично) ❌{}(плохо) из {} тикеров",
                tickersWithGoodData, tickersWithPartialData, tickersWithBadData, cacheResults.size());
    }
    
    /**
     * Логирование финальных результатов
     */
    private void logFinalResults(Map<String, List<Candle>> result, int expectedCandleCount, 
                               long requestDuration, boolean usedCache) {
        if (result.isEmpty()) {
            log.error("💥 ПРОВАЛ: Не получено ни одного тикера с данными!");
            return;
        }
        
        int totalCandles = result.values().stream().mapToInt(List::size).sum();
        int avgCandles = totalCandles / result.size();
        double avgCompleteness = (double) avgCandles / expectedCandleCount * 100;
        
        String cacheStatus = usedCache ? "💾КЭША" : "📡API";
        
        log.info("🏁 АК-47 РЕЗУЛЬТАТ [{}]: {} тикеров, {} свечей (сред.{}, {}%), за {} мс",
                cacheStatus, result.size(), totalCandles, avgCandles, 
                String.format("%.1f", avgCompleteness), requestDuration);
        
        // Показываем примеры тикеров для отладки
        if (result.size() <= 10) {
            log.info("🔍 ТИКЕРЫ: {}", String.join(", ", result.keySet()));
        } else {
            String firstFew = result.keySet().stream().limit(5).reduce((a, b) -> a + ", " + b).orElse("");
            log.info("🔍 ПЕРВЫЕ ТИКЕРЫ: {}... (всего {})", firstFew, result.size());
        }
    }
    
    /**
     * Старая логика пагинации (fallback для прямых API запросов)
     */
    private Map<String, List<Candle>> getCandlesWithPagination(Settings settings, List<String> swapTickers, int totalLimit) {
        log.info("📡 ПАГИНАЦИЯ API: {} свечей для {} тикеров", totalLimit, swapTickers.size());
        
        Map<String, List<Candle>> result = new HashMap<>();
        int batchSize = 300; // Максимум для OKX API
        
        try {
            // Получаем первую пачку
            Settings initialSettings = new Settings();
            initialSettings.copyFrom(settings);
            initialSettings.setCandleLimit(Math.min(batchSize, totalLimit));
            
            Map<String, List<Candle>> initialBatch = getCandles(initialSettings, swapTickers, true);
            
            if (initialBatch.isEmpty()) {
                log.warn("⚠️ ПАГИНАЦИЯ: Не удалось получить начальные данные");
                return result;
            }
            
            // Для каждого тикера собираем дополнительные данные
            for (Map.Entry<String, List<Candle>> entry : initialBatch.entrySet()) {
                String ticker = entry.getKey();
                List<Candle> allCandles = new ArrayList<>(entry.getValue());
                
                if (allCandles.isEmpty()) continue;
                
                int remainingCandles = totalLimit - allCandles.size();
                
                // Добавляем исторические данные
                while (remainingCandles > 0 && allCandles.size() < totalLimit) {
                    try {
                        long oldestTimestamp = allCandles.get(0).getTimestamp();
                        int batchLimit = Math.min(batchSize, remainingCandles);
                        
                        List<Candle> historicalBatch = getCandlesPaginated(ticker, settings.getTimeframe(), 
                                batchLimit, oldestTimestamp);
                        
                        if (historicalBatch.isEmpty()) {
                            break;
                        }
                        
                        allCandles.addAll(0, historicalBatch);
                        remainingCandles -= historicalBatch.size();
                        
                        Thread.sleep(150); // Пауза между запросами
                        
                    } catch (Exception e) {
                        log.warn("⚠️ ПАГИНАЦИЯ: Ошибка для {}: {}", ticker, e.getMessage());
                        break;
                    }
                }
                
                // Обрезаем до нужного размера
                if (allCandles.size() > totalLimit) {
                    allCandles = allCandles.subList(allCandles.size() - totalLimit, allCandles.size());
                }
                
                result.put(ticker, allCandles);
            }
            
        } catch (Exception e) {
            log.error("❌ ПАГИНАЦИЯ: Ошибка {}", e.getMessage(), e);
        }
        
        return result;
    }

    /**
     * Получение исторических свечей с использованием параметра before для пагинации
     */
    private List<Candle> getCandlesPaginated(String ticker, String timeframe, int limit, long beforeTimestamp) {
        try {
            log.debug("🔍 Запрос {} исторических свечей для {} до timestamp {}",
                    limit, ticker, beforeTimestamp);

            // Вызываем OKX API с параметром before для пагинации
            List<Candle> historicalCandles = okxFeignClient.getCandlesBefore(ticker, timeframe, limit, beforeTimestamp);

            log.debug("📊 Получено {} исторических свечей для {} до timestamp {}",
                    historicalCandles.size(), ticker, beforeTimestamp);

            return historicalCandles;

        } catch (Exception e) {
            log.error("❌ Ошибка при получении исторических свечей для {} до {}: {}",
                    ticker, beforeTimestamp, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Проверяет хронологический порядок свечей и логирует проблемы
     */
    private void validateCandlesTimeOrder(String ticker, List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            return;
        }

        boolean hasTimeOrderIssues = false;
        long prevTimestamp = candles.get(0).getTimestamp();

        for (int i = 1; i < candles.size(); i++) {
            long currentTimestamp = candles.get(i).getTimestamp();
            if (currentTimestamp <= prevTimestamp) {
                if (!hasTimeOrderIssues) {
                    log.warn("❌ {}: нарушение хронологического порядка свечей!", ticker);
                    hasTimeOrderIssues = true;
                }
                log.warn("❌ {}: свеча {} (timestamp={}) <= предыдущей {} (timestamp={})",
                        ticker, i, currentTimestamp, i - 1, prevTimestamp);
            }
            prevTimestamp = currentTimestamp;
        }

        if (!hasTimeOrderIssues) {
            log.info("✅ {}: хронологический порядок {} свечей корректен. Диапазон: {} - {}",
                    ticker, candles.size(),
                    candles.get(0).getTimestamp(),
                    candles.get(candles.size() - 1).getTimestamp());
        } else {
            log.error("❌ {}: КРИТИЧЕСКАЯ ОШИБКА - нарушен хронологический порядок свечей! Это может привести к неверным расчетам Z-Score и графикам!", ticker);
        }
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap, Settings settings) {
        if (candlesMap == null) {
            throw new IllegalArgumentException("Мапа свечей не может быть null!");
        }

        double candleLimit = settings.getCandleLimit();
        int minAcceptableCandles = (int) (candleLimit * 0.9); // Принимаем 90% от требуемого количества

        candlesMap.forEach((ticker, candles) -> {
            if (candles == null) {
                log.error("❌ Список свечей для тикера {} равен null!", ticker);
                throw new IllegalArgumentException("Список свечей не может быть null для тикера: " + ticker);
            }

            // Гибкая валидация - принимаем если есть хотя бы 90% от требуемого количества
            if (candles.size() < minAcceptableCandles) {
                log.error(
                        "❌ Недостаточно свечей для тикера {}: получено {}, минимум требуется {}",
                        ticker, candles.size(), minAcceptableCandles
                );
                throw new IllegalArgumentException(
                        String.format(
                                "❌ Недостаточно свечей для тикера %s: %d, минимум требуется: %d (90%% от %.0f)",
                                ticker, candles.size(), minAcceptableCandles, candleLimit
                        )
                );
            }

            // Предупреждение если количество не точно совпадает но в допустимых пределах
            if (candles.size() != (int) candleLimit) {
                log.warn("⚠️ Количество свечей для тикера {} отличается от заданного: получено {}, ожидалось {}",
                        ticker, candles.size(), (int) candleLimit);
            }
        });
    }
}
