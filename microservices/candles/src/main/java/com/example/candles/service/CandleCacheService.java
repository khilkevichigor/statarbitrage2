package com.example.candles.service;

import com.example.candles.client.OkxFeignClient;
import com.example.candles.repositories.CachedCandleRepository;
import com.example.shared.dto.Candle;
import com.example.shared.models.CachedCandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.Comparator;
import org.springframework.data.domain.PageRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandleCacheService {

    private final CachedCandleRepository cachedCandleRepository;
    private final OkxFeignClient okxFeignClient;
    private final CandleTransactionService candleTransactionService;

    @Value("${app.candle-cache.default-exchange:OKX}")
    private String defaultExchange;
    
    @Value("${app.candle-cache.thread-pool-size:5}")
    private int threadPoolSize;
    
    // Пул потоков для параллельной загрузки (настраивается через properties)
    private ExecutorService executorService;

    private final Map<String, Integer> defaultCachePeriods = Map.of(
            "1m", 365,    // 1 год для мелких таймфреймов
            "5m", 365,
            "15m", 365,
            "1H", 1095,   // 3 года для средних таймфреймов  
            "4H", 1095,
            "1D", 1825,   // 5 лет для крупных таймфреймов
            "1W", 1825,
            "1M", 1825
    );

    @PostConstruct
    public void initializeExecutorService() {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        log.info("🔧 Инициализирован ExecutorService с {} потоками", threadPoolSize);
    }

    @PreDestroy
    public void shutdownExecutorService() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            log.info("🛑 ExecutorService остановлен");
        }
    }

    /**
     * Обновить количество потоков в пуле
     */
    public synchronized void updateThreadPoolSize(int newThreadPoolSize) {
        if (newThreadPoolSize <= 0 || newThreadPoolSize > 20) {
            log.warn("❌ Некорректное количество потоков: {}. Используйте от 1 до 20.", newThreadPoolSize);
            return;
        }

        if (newThreadPoolSize == this.threadPoolSize) {
            log.info("ℹ️ Количество потоков уже равно {}, обновление не требуется", newThreadPoolSize);
            return;
        }

        log.info("🔄 Обновляем пул потоков с {} на {} потоков", this.threadPoolSize, newThreadPoolSize);
        
        // Останавливаем старый пул
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        // Создаем новый пул
        this.threadPoolSize = newThreadPoolSize;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        
        log.info("✅ Пул потоков обновлен до {} потоков", threadPoolSize);
    }

    /**
     * Получить текущее количество потоков
     */
    public int getCurrentThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * 🔍 ВАЛИДАЦИЯ СВЕЧЕЙ для коинтеграции
     * Проверяем что у всех тикеров одинаковое количество свечей 
     * и одинаковые таймштампы первой и последней свечи
     */
    private void validateCandlesConsistency(Map<String, List<Candle>> candlesMap, 
                                           String timeframe, int expectedLimit) {
        if (candlesMap.isEmpty()) {
            log.warn("⚠️ ВАЛИДАЦИЯ: Пустая карта свечей для валидации");
            return;
        }

        log.info("🔍 ВАЛИДАЦИЯ: Проверяем консистентность {} тикеров (таймфрейм: {}, лимит: {})",
                candlesMap.size(), timeframe, expectedLimit);

        // Статистика для валидации
        Map<Integer, Integer> candleCountDistribution = new HashMap<>();
        Map<Long, Integer> firstTimestampDistribution = new HashMap<>(); 
        Map<Long, Integer> lastTimestampDistribution = new HashMap<>();
        List<String> validTickers = new ArrayList<>();
        List<String> invalidTickers = new ArrayList<>();

        long expectedFirstTimestamp = -1;
        long expectedLastTimestamp = -1;
        int expectedCandleCount = -1;

        // Проходим по всем тикерам и собираем статистику
        for (Map.Entry<String, List<Candle>> entry : candlesMap.entrySet()) {
            String ticker = entry.getKey();
            List<Candle> candles = entry.getValue();

            if (candles == null || candles.isEmpty()) {
                invalidTickers.add(ticker + "(пустой)");
                continue;
            }

            // Сортируем свечи по timestamp для корректной валидации
            candles.sort(Comparator.comparingLong(Candle::getTimestamp));

            int candleCount = candles.size();
            long firstTimestamp = candles.get(0).getTimestamp();
            long lastTimestamp = candles.get(candles.size() - 1).getTimestamp();

            // Собираем статистику распределения
            candleCountDistribution.merge(candleCount, 1, Integer::sum);
            firstTimestampDistribution.merge(firstTimestamp, 1, Integer::sum);
            lastTimestampDistribution.merge(lastTimestamp, 1, Integer::sum);

            // Устанавливаем эталонные значения с первого тикера
            if (expectedFirstTimestamp == -1) {
                expectedFirstTimestamp = firstTimestamp;
                expectedLastTimestamp = lastTimestamp;
                expectedCandleCount = candleCount;
            }

            // Проверяем соответствие эталону
            boolean isValid = (candleCount == expectedCandleCount && 
                             firstTimestamp == expectedFirstTimestamp &&
                             lastTimestamp == expectedLastTimestamp);

            if (isValid) {
                validTickers.add(ticker);
            } else {
                String reason = String.format("(свечей:%d≠%d, начало:%d≠%d, конец:%d≠%d)", 
                    candleCount, expectedCandleCount, 
                    firstTimestamp, expectedFirstTimestamp,
                    lastTimestamp, expectedLastTimestamp);
                invalidTickers.add(ticker + reason);
            }
        }

        // Форматируем временные метки для лучшей читаемости
        String firstTimeStr = "неизвестно";
        String lastTimeStr = "неизвестно";
        
        if (expectedFirstTimestamp > 0) {
            // Проверяем формат timestamp: если слишком большой, то в миллисекундах, иначе в секундах
            if (expectedFirstTimestamp > 9999999999L) { // больше чем 2001 год в секундах
                firstTimeStr = java.time.Instant.ofEpochMilli(expectedFirstTimestamp).toString();
            } else {
                firstTimeStr = java.time.Instant.ofEpochSecond(expectedFirstTimestamp).toString();
            }
        }
        
        if (expectedLastTimestamp > 0) {
            if (expectedLastTimestamp > 9999999999L) {
                lastTimeStr = java.time.Instant.ofEpochMilli(expectedLastTimestamp).toString();
            } else {
                lastTimeStr = java.time.Instant.ofEpochSecond(expectedLastTimestamp).toString();
            }
        }

        // 📊 ДЕТАЛЬНЫЙ ОТЧЕТ О ВАЛИДАЦИИ
        log.info("📊 ВАЛИДАЦИЯ РЕЗУЛЬТАТ:");
        log.info("   🎯 Эталонные значения: {} свечей, {} - {}", 
                expectedCandleCount, firstTimeStr, lastTimeStr);
        log.info("   ✅ Валидные тикеры: {} из {} ({}%)", 
                validTickers.size(), candlesMap.size(), 
                Math.round(100.0 * validTickers.size() / candlesMap.size()));

        if (!invalidTickers.isEmpty()) {
            log.warn("   ❌ Невалидные тикеры ({}): {}", 
                    invalidTickers.size(), invalidTickers.size() <= 10 ? 
                    String.join(", ", invalidTickers) : 
                    String.join(", ", invalidTickers.subList(0, 10)) + "...");
        }

        // Статистика распределений
        log.info("   📈 Распределение по количеству свечей: {}", candleCountDistribution);
        
        if (firstTimestampDistribution.size() > 1) {
            log.warn("   ⚠️ Разные начальные таймштампы: {} вариантов", firstTimestampDistribution.size());
        }
        
        if (lastTimestampDistribution.size() > 1) {
            log.warn("   ⚠️ Разные конечные таймштампы: {} вариантов", lastTimestampDistribution.size());
        }

        // Финальная оценка качества данных
        double consistencyRate = (double) validTickers.size() / candlesMap.size();
        String percentStr = String.format("%.1f%%", consistencyRate * 100);
        
        if (consistencyRate >= 0.95) {
            log.info("🎉 ВАЛИДАЦИЯ: Отличная консистентность данных ({})", percentStr);
        } else if (consistencyRate >= 0.90) {
            log.warn("⚠️ ВАЛИДАЦИЯ: Хорошая консистентность данных ({})", percentStr);
        } else if (consistencyRate >= 0.80) {
            log.warn("❌ ВАЛИДАЦИЯ: Средняя консистентность данных ({})", percentStr);
        } else {
            log.error("💥 ВАЛИДАЦИЯ: ПЛОХАЯ консистентность данных ({}) - НЕ РЕКОМЕНДУЕТСЯ для коинтеграции!", percentStr);
        }
    }

    public Map<String, List<Candle>> getCachedCandles(List<String> tickers, String timeframe,
                                                      int candleLimit) {
        return getCachedCandles(tickers, timeframe, candleLimit, defaultExchange);
    }

    public Map<String, List<Candle>> getCachedCandles(List<String> tickers, String timeframe,
                                                      int candleLimit, String exchange) {
        log.info("🔍 Запрос кэшированных свечей: {} тикеров, таймфрейм {}, лимит {}",
                tickers.size(), timeframe, candleLimit);

        Map<String, List<Candle>> result = new ConcurrentHashMap<>();
        Map<String, Integer> missingCandlesCount = new ConcurrentHashMap<>();
        int cacheHits = 0; // Счетчик тикеров полученных из кэша
        int totalCandlesAdded = 0; // Счетчик реально добавленных свечей в БД

        long currentTimestamp = System.currentTimeMillis() / 1000;
        long requiredFromTimestamp = calculateFromTimestamp(currentTimestamp, timeframe, candleLimit);

        // ЧЕТКАЯ ЛОГИКА: Проверяем что есть в кэше, если меньше чем запрошено - догружаем
        
        int debugCount = 0; // Для отладки - покажем первые 5 тикеров
        for (String ticker : tickers) {
            if (debugCount < 5) debugCount++;
            // ИСПРАВЛЕНО: Проверяем последние N свечей для этого тикера
            List<CachedCandle> latestCandles = cachedCandleRepository
                    .findLatestByTickerTimeframeExchange(ticker, timeframe, exchange, 
                            PageRequest.of(0, candleLimit));

            log.info("🔍 DEBUG: Для {} найдено {} последних свечей в кэше (запрошено {})",
                    ticker, latestCandles.size(), candleLimit);

            if (!latestCandles.isEmpty()) {
                // Есть данные в кэше - всегда их берем
                List<Candle> candlesList = latestCandles.stream()
                        .map(CachedCandle::toCandle)
                        .sorted(Comparator.comparing(Candle::getTimestamp))
                        .collect(Collectors.toList());
                        
                result.put(ticker, candlesList);
                cacheHits++; // Увеличиваем счетчик кэш-хитов
                
                // ЧЕТКО: Если данных меньше запрошенного количества - догружаем недостающие
                if (latestCandles.size() < candleLimit) {
                    int missing = candleLimit - latestCandles.size();
                    missingCandlesCount.put(ticker, missing);
                    if (debugCount <= 5) {
                        log.info("🔄 Кэш PARTIAL: {} - есть {}, нужно {}, догрузим {}", 
                                ticker, latestCandles.size(), candleLimit, missing);
                    }
                } else {
                    if (debugCount <= 5) {
                        log.info("✅ Кэш HIT: {} - {} свечей из кэша (полное покрытие)", ticker, latestCandles.size());
                    }
                }
            } else {
                // Нет данных в кэше - загружаем полное количество
                missingCandlesCount.put(ticker, candleLimit);
                log.info("❌ Кэш MISS: {} - нет данных, загрузим {} свечей", ticker, candleLimit);
            }
        }

        // Загружаем недостающие свечи
        if (!missingCandlesCount.isEmpty()) {
            log.info("🔄 Догружаем {} тикеров с недостающими свечами", missingCandlesCount.size());
            Map<String, Object> loadingResult = loadMissingCandles(missingCandlesCount,
                    timeframe, exchange, requiredFromTimestamp);
            @SuppressWarnings("unchecked")
            Map<String, List<Candle>> missingCandles = (Map<String, List<Candle>>) loadingResult.get("candles");
            totalCandlesAdded = (Integer) loadingResult.get("addedCount");
            result.putAll(missingCandles);
        }

        log.info("✅ ИТОГО: {} тикеров (кэш: {}, догружено: {}, добавлено в БД: {} свечей)",
                result.size(), cacheHits, missingCandlesCount.size(), totalCandlesAdded);

        // 🔍 ВАЛИДАЦИЯ СВЕЧЕЙ для коинтеграции
        validateCandlesConsistency(result, timeframe, candleLimit);

        return result;
    }

    public void preloadAllCandles(String exchange) {
        log.info("🚀 Запуск полной предзагрузки свечей для биржи {}", exchange);
        int totalCandlesAdded = 0;

        try {
            // Получаем все SWAP тикеры
            List<String> allTickers = okxFeignClient.getAllSwapTickers(true);
            log.info("📊 Найдено {} SWAP тикеров для предзагрузки", allTickers.size());

            // Загружаем по каждому таймфрейму
            for (Map.Entry<String, Integer> entry : defaultCachePeriods.entrySet()) {
                String timeframe = entry.getKey();
                int periodDays = entry.getValue();

                log.info("⏰ Предзагрузка таймфрейма {} ({} дней) для {} тикеров",
                        timeframe, periodDays, allTickers.size());

                int addedForTimeframe = preloadTimeframeForTickers(allTickers, timeframe, exchange, periodDays);
                totalCandlesAdded += addedForTimeframe;
                
                log.info("📊 Таймфрейм {} завершен: добавлено {} свечей в БД", timeframe, addedForTimeframe);

                // Небольшая пауза между таймфреймами
                Thread.sleep(1000);
            }

            log.info("✅ ШЕДУЛЛЕР: Полная предзагрузка завершена для биржи {} - добавлено {} свечей в БД", 
                    exchange, totalCandlesAdded);

        } catch (Exception e) {
            log.error("❌ Ошибка при полной предзагрузке: {}", e.getMessage(), e);
        }
    }

    public void dailyCandlesUpdate(String exchange) {
        log.info("🔄 Запуск ежедневного обновления свечей для биржи {}", exchange);
        int totalCandlesAdded = 0;

        try {
            List<String> cachedTickers = cachedCandleRepository.findDistinctTickersByExchange(exchange);
            log.info("📊 Обновляем {} тикеров из кэша", cachedTickers.size());

            long currentTimestamp = System.currentTimeMillis() / 1000;
            // Обновляем последние 48 часов для безопасности
            long updateFromTimestamp = currentTimestamp - (48 * 3600);

            for (String timeframe : defaultCachePeriods.keySet()) {
                log.info("⏰ Обновление таймфрейма {} для {} тикеров", timeframe, cachedTickers.size());

                int addedForTimeframe = updateCandlesForTickers(cachedTickers, timeframe, exchange, updateFromTimestamp);
                totalCandlesAdded += addedForTimeframe;
                
                log.info("📊 Таймфрейм {} обновлен: добавлено {} свечей в БД", timeframe, addedForTimeframe);

                Thread.sleep(500);
            }

            log.info("✅ ШЕДУЛЛЕР: Ежедневное обновление завершено для биржи {} - добавлено {} свечей в БД", 
                    exchange, totalCandlesAdded);

        } catch (Exception e) {
            log.error("❌ Ошибка при ежедневном обновлении: {}", e.getMessage(), e);
        }
    }

    public Map<String, Object> getCacheStatistics(String exchange) {
        Map<String, Object> stats = new HashMap<>();

        try {
            List<Object[]> rawStats = cachedCandleRepository.getCacheStatistics();
            Map<String, Map<String, Long>> exchangeStats = new HashMap<>();

            for (Object[] row : rawStats) {
                String ex = (String) row[0];
                String tf = (String) row[1];
                Long count = (Long) row[2];

                exchangeStats.computeIfAbsent(ex, k -> new HashMap<>()).put(tf, count);
            }

            stats.put("byExchange", exchangeStats);

            // Дополнительная статистика для конкретной биржи
            if (exchange != null) {
                List<String> tickers = cachedCandleRepository.findDistinctTickersByExchange(exchange);
                stats.put("totalTickers", tickers.size());

                Map<String, Long> timeframeStats = new HashMap<>();
                for (String timeframe : defaultCachePeriods.keySet()) {
                    Long count = cachedCandleRepository.countByTickerTimeframeExchange("", timeframe, exchange);
                    timeframeStats.put(timeframe, count);
                }
                stats.put("timeframeStats", timeframeStats);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка получения статистики кэша: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    public void forceLoadCandlesCustom(String exchange, java.util.Set<String> timeframes, 
                                      List<String> tickers, Integer threadCount, Integer periodDays) {
        log.info("🎯 ПРИНУДИТЕЛЬНАЯ ЗАГРУЗКА: биржа={}, таймфреймы={}, тикеров={}, потоки={}, период={} дней", 
                exchange, timeframes, tickers != null ? tickers.size() : 0, threadCount, periodDays);
        
        try {
            // Определяем список тикеров для загрузки
            List<String> targetTickers = tickers;
            if (targetTickers == null || targetTickers.isEmpty()) {
                // Загружаем все SWAP тикеры если не указаны конкретные
                targetTickers = okxFeignClient.getAllSwapTickers(true);
                log.info("📊 Загружаем все доступные {} SWAP тикеров", targetTickers.size());
            } else {
                log.info("📊 Загружаем указанные {} тикеров", targetTickers.size());
            }
            
            // Загружаем по каждому таймфрейму
            int totalCandlesAdded = 0;
            for (String timeframe : timeframes) {
                log.info("⏰ Принудительная загрузка таймфрейма {} для {} тикеров", 
                        timeframe, targetTickers.size());
                
                int candleLimit = calculateCandleLimit(timeframe, periodDays != null ? periodDays : 365);
                int addedForTimeframe = preloadTimeframeForTickers(targetTickers, timeframe, exchange, periodDays != null ? periodDays : 365);
                totalCandlesAdded += addedForTimeframe;
                
                log.info("📊 Таймфрейм {} завершен: добавлено {} свечей в БД", timeframe, addedForTimeframe);
                
                // Пауза между таймфреймами
                Thread.sleep(1000);
            }
            
            log.info("✅ ПРИНУДИТЕЛЬНАЯ ЗАГРУЗКА завершена для биржи {} - добавлено {} свечей в БД", 
                    exchange, totalCandlesAdded);
            
        } catch (Exception e) {
            log.error("❌ Ошибка при принудительной загрузке: {}", e.getMessage(), e);
        }
    }

    private int preloadTimeframeForTickers(List<String> tickers, String timeframe,
                                            String exchange, int periodDays) {
        int candleLimit = calculateCandleLimit(timeframe, periodDays);
        final int[] totalAddedCount = {0}; // Используем массив для thread-safe изменения

        log.info("📈 МНОГОПОТОЧНАЯ предзагрузка {} свечей типа {} для {} тикеров ({} потоков)",
                candleLimit, timeframe, tickers.size(), threadPoolSize);

        // Уменьшаем размер батча для многопоточности
        int batchSize = Math.max(1, getBatchSizeForTimeframe(timeframe, candleLimit) / 2);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < tickers.size(); i += batchSize) {
            final int batchIndex = i;
            final List<String> batch = tickers.subList(i, Math.min(i + batchSize, tickers.size()));

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    log.info("🚀 ПОТОК: Обрабатываем батч {}-{}", batchIndex + 1,
                            Math.min(batchIndex + batchSize, tickers.size()));
                    
                    Map<String, List<Candle>> candlesMap = loadCandlesForBatch(batch, timeframe, candleLimit);
                    int batchAddedCount = 0;

                    // Сохраняем в кэш каждый тикер отдельно
                    for (Map.Entry<String, List<Candle>> entry : candlesMap.entrySet()) {
                        String ticker = entry.getKey();
                        List<Candle> candles = entry.getValue();

                        int addedCount = candleTransactionService.saveCandlesToCache(ticker, timeframe, exchange, candles);
                        batchAddedCount += addedCount;
                        
                        if (addedCount > 0) {
                            log.info("✅ ПОТОК: {} - добавлено {} свечей в БД", ticker, addedCount);
                        }
                    }

                    synchronized (totalAddedCount) {
                        totalAddedCount[0] += batchAddedCount;
                    }

                    log.info("✅ ПОТОК: Батч {}-{} завершен, добавлено {} свечей в БД",
                            batchIndex + 1, Math.min(batchIndex + batchSize, tickers.size()), batchAddedCount);

                } catch (Exception e) {
                    log.error("❌ ПОТОК: Ошибка обработки батча {}-{}: {}", batchIndex + 1,
                            Math.min(batchIndex + batchSize, tickers.size()), e.getMessage(), e);
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Ждем завершения всех потоков
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            log.info("🏁 ВСЕ 5 ПОТОКОВ: Завершена многопоточная обработка {} тикеров", tickers.size());
        } catch (Exception e) {
            log.error("❌ ВСЕ ПОТОКИ: Ошибка при ожидании завершения потоков: {}", e.getMessage(), e);
        }

        return totalAddedCount[0];
    }

    private int updateCandlesForTickers(List<String> tickers, String timeframe,
                                         String exchange, long fromTimestamp) {
        // Аналогично preloadTimeframeForTickers, но только для недавних свечей
        int batchSize = 20;
        int totalAddedCount = 0;
        
        for (int i = 0; i < tickers.size(); i += batchSize) {
            List<String> batch = tickers.subList(i, Math.min(i + batchSize, tickers.size()));

            try {
                // Рассчитываем сколько свечей нужно для 48 часов
                int candleLimit = calculateCandleLimit(timeframe, 2); // 2 дня

                Map<String, List<Candle>> candlesMap = loadCandlesForBatch(batch, timeframe, candleLimit);

                for (Map.Entry<String, List<Candle>> entry : candlesMap.entrySet()) {
                    String ticker = entry.getKey();
                    List<Candle> candles = entry.getValue();

                    int addedCount = candleTransactionService.updateCandlesInCache(ticker, timeframe, exchange, candles, fromTimestamp);
                    totalAddedCount += addedCount;
                    
                    if (addedCount > 0) {
                        log.info("✅ ОБНОВЛЕНИЕ: {} - добавлено {} свечей в БД", ticker, addedCount);
                    }
                }

                Thread.sleep(100);

            } catch (Exception e) {
                log.warn("⚠️ Ошибка обновления батча: {}", e.getMessage());
            }
        }
        
        return totalAddedCount;
    }

    private Map<String, Object> loadMissingCandles(Map<String, Integer> missingCandlesCount,
                                                         String timeframe, String exchange,
                                                         long requiredFromTimestamp) {
        Map<String, List<Candle>> result = new ConcurrentHashMap<>();
        final int[] totalAddedCount = {0}; // Используем массив для thread-safe изменения

        try {
            log.info("🚀 МНОГОПОТОЧНАЯ догрузка недостающих свечей в 5 потоков для {} тикеров", 
                    missingCandlesCount.size());

            // ИСПРАВЛЕНО: Многопоточная загрузка недостающих данных в 5 потоков
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (Map.Entry<String, Integer> entry : missingCandlesCount.entrySet()) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    String ticker = entry.getKey();
                    int missingCount = entry.getValue();

                    try {
                        log.info("🔄 ПОТОК: Загружаем {} недостающих свечей для {}", missingCount, ticker);

                        // Определяем какие именно свечи отсутствуют
                        List<CachedCandle> existingCandles = cachedCandleRepository
                                .findByTickerTimeframeExchangeFromTimestamp(ticker, timeframe, exchange, requiredFromTimestamp);

                        // Если есть данные в кэше - определяем самую старую дату и загружаем данные до неё
                        List<Candle> loadedCandles;
                        if (!existingCandles.isEmpty()) {
                            // Есть данные - загружаем только старые недостающие ДО самой старой записи
                            long oldestTimestamp = existingCandles.stream()
                                    .mapToLong(CachedCandle::getTimestamp)
                                    .min().orElse(System.currentTimeMillis() / 1000);
                            
                            log.info("🔄 ПОТОК: Для {} загружаем {} исторических свечей до {}",
                                    ticker, missingCount, new java.util.Date(oldestTimestamp * 1000));
                            
                            // ИСПРАВЛЕНО: Загружаем исторические данные ДО oldestTimestamp
                            loadedCandles = loadCandlesBeforeTimestamp(ticker, timeframe, missingCount, oldestTimestamp);
                        } else {
                            // Нет данных - загружаем полное количество (последние свечи)
                            log.info("🔄 ПОТОК: Для {} загружаем полное количество {} свечей", ticker, missingCount);
                            loadedCandles = loadCandlesWithPagination(ticker, timeframe, missingCount);
                        }

                        if (!loadedCandles.isEmpty()) {
                            // Сохраняем в кэш и получаем количество реально добавленных свечей
                            int addedCount = candleTransactionService.saveCandlesToCache(ticker, timeframe, exchange, loadedCandles);
                            
                            synchronized (totalAddedCount) {
                                totalAddedCount[0] += addedCount;
                            }

                            // ПОЛУЧАЕМ АКТУАЛЬНЫЕ ДАННЫЕ ИЗ КЭША ПОСЛЕ СОХРАНЕНИЯ
                            List<CachedCandle> updatedCachedCandles = cachedCandleRepository
                                    .findLatestByTickerTimeframeExchange(ticker, timeframe, exchange, 
                                            PageRequest.of(0, missingCount + existingCandles.size()));

                            List<Candle> finalCandles = updatedCachedCandles.stream()
                                    .map(CachedCandle::toCandle)
                                    .sorted(Comparator.comparing(Candle::getTimestamp))
                                    .collect(Collectors.toList());

                            result.put(ticker, finalCandles);
                            
                            if (addedCount > 0) {
                                log.info("✅ ПОТОК: Для {} добавлено {} свечей в БД, получено {} из кэша",
                                        ticker, addedCount, finalCandles.size());
                            }
                        }

                    } catch (Exception e) {
                        log.error("❌ ПОТОК: Ошибка загрузки для тикера {}: {}", ticker, e.getMessage());
                    }
                }, executorService);
                
                futures.add(future);
            }

            // Ждем завершения всех потоков
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                log.info("🏁 ВСЕ 5 ПОТОКОВ: Завершена многопоточная догрузка {} тикеров", missingCandlesCount.size());
            } catch (Exception e) {
                log.error("❌ ВСЕ ПОТОКИ: Ошибка при ожидании завершения потоков догрузки: {}", e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка загрузки недостающих свечей: {}", e.getMessage(), e);
        }

        // Возвращаем и результаты и счетчик
        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("candles", result);
        finalResult.put("addedCount", totalAddedCount[0]);
        
        return finalResult;
    }



    private long calculateFromTimestamp(long currentTimestamp, String timeframe, int candleLimit) {
        int timeframeSeconds = getTimeframeInSeconds(timeframe);
        return currentTimestamp - ((long) candleLimit * timeframeSeconds);
    }

    private int calculateCandleLimit(String timeframe, int periodDays) {
        return switch (timeframe) {
            case "1m" -> periodDays * 24 * 60;
            case "5m" -> periodDays * 24 * 12;
            case "15m" -> periodDays * 24 * 4;
            case "1H" -> periodDays * 24;
            case "4H" -> periodDays * 6;
            case "1D" -> periodDays;
            case "1W" -> periodDays / 7;
            case "1M" -> periodDays / 30;
            default -> periodDays * 24; // По умолчанию как 1H
        };
    }

    private int getTimeframeInSeconds(String timeframe) {
        return switch (timeframe) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "1H" -> 3600;
            case "4H" -> 14400;
            case "1D" -> 86400;
            case "1W" -> 604800;
            case "1M" -> 2592000; // примерно 30 дней
            default -> 3600; // По умолчанию как 1H
        };
    }

    /**
     * Определяет размер батча в зависимости от таймфрейма и объема данных
     */
    private int getBatchSizeForTimeframe(String timeframe, int candleLimit) {
        // Для очень больших объемов данных уменьшаем размер батча
        if (candleLimit > 100000) { // > 100K свечей
            return switch (timeframe) {
                case "1m" -> 1;  // По одному тикеру для минутных свечей
                case "5m", "15m" -> 2;  // По 2 тикера
                default -> 5;
            };
        } else if (candleLimit > 10000) { // > 10K свечей
            return switch (timeframe) {
                case "1m", "5m" -> 3;
                case "15m", "1H" -> 5;
                default -> 10;
            };
        } else {
            return 20; // Стандартный размер батча для небольших объемов
        }
    }

    /**
     * Загружает свечи для батча тикеров используя доступные методы OkxFeignClient
     */
    private Map<String, List<Candle>> loadCandlesForBatch(List<String> tickers, String timeframe, int candleLimit) {
        Map<String, List<Candle>> result = new HashMap<>();

        try {
            if (candleLimit <= 300) {
                // Простой случай - используем стандартный метод
                result = okxFeignClient.getCandlesMap(tickers, timeframe, candleLimit, false);
            } else {
                // Сложный случай - нужна пагинация для каждого тикера
                for (String ticker : tickers) {
                    try {
                        List<Candle> candlesForTicker = loadCandlesWithPagination(ticker, timeframe, candleLimit);
                        if (!candlesForTicker.isEmpty()) {
                            result.put(ticker, candlesForTicker);
                        }

                        // Небольшая пауза между запросами
                        Thread.sleep(150);

                    } catch (Exception e) {
                        log.warn("⚠️ Ошибка загрузки свечей для {}: {}", ticker, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка загрузки батча свечей: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Загружает свечи с пагинацией для одного тикера
     */
    private List<Candle> loadCandlesWithPagination(String ticker, String timeframe, int totalLimit) {
        List<Candle> allCandles = new ArrayList<>();
        int batchSize = 300; // Максимум для OKX API
        Long beforeTimestamp = null;

        try {
            while (allCandles.size() < totalLimit) {
                int remainingCandles = totalLimit - allCandles.size();
                int currentBatchSize = Math.min(batchSize, remainingCandles);

                List<Candle> batchCandles;
                if (beforeTimestamp == null) {
                    // Первый запрос - получаем последние свечи
                    batchCandles = okxFeignClient.getCandles(ticker, timeframe, currentBatchSize);
                } else {
                    // Последующие запросы - используем пагинацию
                    batchCandles = okxFeignClient.getCandlesBefore(ticker, timeframe, currentBatchSize, beforeTimestamp);
                }

                if (batchCandles == null || batchCandles.isEmpty()) {
                    break; // Больше нет данных
                }

                allCandles.addAll(batchCandles);

                // Устанавливаем timestamp для следующего запроса
                beforeTimestamp = batchCandles.get(batchCandles.size() - 1).getTimestamp();

                // Пауза между запросами для соблюдения rate limit
                Thread.sleep(120);

                log.info("📥 Загружено {} свечей для {} (всего: {})",
                        batchCandles.size(), ticker, allCandles.size());
            }

            // Сортируем по времени (от старых к новым)
            allCandles.sort(Comparator.comparing(Candle::getTimestamp));

        } catch (Exception e) {
            log.error("❌ Ошибка пагинации свечей для {}: {}", ticker, e.getMessage(), e);
        }

        return allCandles;
    }

    /**
     * Загружает исторические свечи ДО указанного timestamp (исправляет циклическую загрузку)
     */
    private List<Candle> loadCandlesBeforeTimestamp(String ticker, String timeframe, int totalLimit, long beforeTimestamp) {
        List<Candle> allCandles = new ArrayList<>();
        int batchSize = 300; // Максимум для OKX API
        Long currentBeforeTimestamp = beforeTimestamp;

        try {
            log.info("🔍 ИСТОРИЧЕСКИЕ: Загружаем {} свечей для {} ДО {}", 
                    totalLimit, ticker, new java.util.Date(beforeTimestamp * 1000));

            while (allCandles.size() < totalLimit) {
                int remainingCandles = totalLimit - allCandles.size();
                int currentBatchSize = Math.min(batchSize, remainingCandles);

                // ИСПРАВЛЕНО: Всегда загружаем исторические данные ДО указанного timestamp
                List<Candle> batchCandles = okxFeignClient.getCandlesBefore(ticker, timeframe, currentBatchSize, currentBeforeTimestamp);

                if (batchCandles == null || batchCandles.isEmpty()) {
                    log.info("📥 ИСТОРИЧЕСКИЕ: Нет больше данных до {}", new java.util.Date(currentBeforeTimestamp * 1000));
                    break; // Больше нет исторических данных
                }

                // Фильтруем свечи которые действительно старше нашего порога
                List<Candle> filteredCandles = batchCandles.stream()
                        .filter(candle -> candle.getTimestamp() < beforeTimestamp)
                        .collect(Collectors.toList());

                if (filteredCandles.isEmpty()) {
                    log.info("📥 ИСТОРИЧЕСКИЕ: Все полученные свечи новее порога {}", new java.util.Date(beforeTimestamp * 1000));
                    break;
                }

                allCandles.addAll(filteredCandles);

                // Устанавливаем timestamp для следующего запроса (самая старая из полученных)
                currentBeforeTimestamp = filteredCandles.get(filteredCandles.size() - 1).getTimestamp();

                // Пауза между запросами для соблюдения rate limit
                Thread.sleep(120);

                log.info("📥 ИСТОРИЧЕСКИЕ: Загружено {} исторических свечей для {} (всего: {})",
                        filteredCandles.size(), ticker, allCandles.size());
            }

            // Сортируем по времени (от старых к новым)
            allCandles.sort(Comparator.comparing(Candle::getTimestamp));

            log.info("✅ ИСТОРИЧЕСКИЕ: Загружено {} исторических свечей для {} до {}", 
                    allCandles.size(), ticker, new java.util.Date(beforeTimestamp * 1000));

        } catch (Exception e) {
            log.error("❌ ИСТОРИЧЕСКИЕ: Ошибка загрузки исторических свечей для {}: {}", ticker, e.getMessage(), e);
        }

        return allCandles;
    }
}