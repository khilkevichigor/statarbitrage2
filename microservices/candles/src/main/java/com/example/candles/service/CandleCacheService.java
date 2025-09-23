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
            log.info("i️ Количество потоков уже равно {}, обновление не требуется", newThreadPoolSize);
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
     * Используем BTC-USDT-SWAP как эталон для проверки консистентности данных
     * Проверяем что у всех тикеров одинаковое количество свечей 
     * и одинаковые таймштампы первой и последней свечи
     * 
     * @return Map<String, List<Candle>> отфильтрованная карта только с валидными тикерами
     */
    private Map<String, List<Candle>> validateAndFilterCandlesConsistency(
            Map<String, List<Candle>> candlesMap, String timeframe, int expectedLimit) {
        if (candlesMap.isEmpty()) {
            log.warn("⚠️ ВАЛИДАЦИЯ: Пустая карта свечей для валидации");
            return new ConcurrentHashMap<>();
        }

        log.info("🔍 ВАЛИДАЦИЯ: Проверяем консистентность {} тикеров (таймфрейм: {}, лимит: {})",
                candlesMap.size(), timeframe, expectedLimit);

        // Статистика для валидации
        Map<Integer, Integer> candleCountDistribution = new HashMap<>();
        Map<Long, Integer> firstTimestampDistribution = new HashMap<>(); 
        Map<Long, Integer> lastTimestampDistribution = new HashMap<>();
        List<String> validTickers = new ArrayList<>();
        List<String> invalidTickers = new ArrayList<>();
        Map<String, List<Candle>> validCandlesMap = new ConcurrentHashMap<>();

        long expectedFirstTimestamp = -1;
        long expectedLastTimestamp = -1;
        int expectedCandleCount = -1;

        // 🎯 ИСПОЛЬЗУЕМ BTC-USDT-SWAP КАК ЭТАЛОН
        final String btcTicker = "BTC-USDT-SWAP";
        List<Candle> btcCandles = candlesMap.get(btcTicker);
        
        if (btcCandles != null && !btcCandles.isEmpty()) {
            btcCandles.sort(Comparator.comparingLong(Candle::getTimestamp));
            expectedCandleCount = btcCandles.size();
            expectedFirstTimestamp = btcCandles.get(0).getTimestamp();
            expectedLastTimestamp = btcCandles.get(btcCandles.size() - 1).getTimestamp();
            
            log.info("🎯 ЭТАЛОН: Используем BTC-USDT-SWAP - {} свечей, {} - {}", 
                    expectedCandleCount, 
                    formatTimestamp(expectedFirstTimestamp),
                    formatTimestamp(expectedLastTimestamp));
        } else {
            log.warn("⚠️ ЭТАЛОН: BTC-USDT-SWAP не найден в данных, используем первый доступный тикер");
        }

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

            // Если BTC не найден, устанавливаем эталон с первого тикера
            if (expectedFirstTimestamp == -1) {
                expectedFirstTimestamp = firstTimestamp;
                expectedLastTimestamp = lastTimestamp;
                expectedCandleCount = candleCount;
                log.warn("⚠️ РЕЗЕРВНЫЙ ЭТАЛОН: Используем {} как эталон", ticker);
            }

            // Проверяем соответствие эталону
            boolean isValid = (candleCount == expectedCandleCount && 
                             firstTimestamp == expectedFirstTimestamp &&
                             lastTimestamp == expectedLastTimestamp);

            if (isValid) {
                validTickers.add(ticker);
                validCandlesMap.put(ticker, candles); // ✅ Добавляем только валидные тикеры
            } else {
                String reason = String.format("(свечей:%d≠%d, начало:%s≠%s, конец:%s≠%s)", 
                    candleCount, expectedCandleCount, 
                    formatTimestamp(firstTimestamp), formatTimestamp(expectedFirstTimestamp),
                    formatTimestamp(lastTimestamp), formatTimestamp(expectedLastTimestamp));
                invalidTickers.add(ticker + reason);
            }
        }

        // Форматируем временные метки для лучшей читаемости
        String firstTimeStr = formatTimestamp(expectedFirstTimestamp);
        String lastTimeStr = formatTimestamp(expectedLastTimestamp);

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
            log.error("💥 ВАЛИДАЦИЯ: ПЛОХАЯ консистентность данных ({}) - исключаем невалидные тикеры!", percentStr);
        }
        
        // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Убеждаемся что все валидные тикеры действительно консистентны между собой
        if (validCandlesMap.size() > 1) {
            log.info("🔍 ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Проверяем взаимную консистентность {} валидных тикеров", validCandlesMap.size());
            
            // Собираем все размеры и временные диапазоны валидных тикеров
            Map<String, String> tickerStats = new HashMap<>();
            for (Map.Entry<String, List<Candle>> entry : validCandlesMap.entrySet()) {
                String ticker = entry.getKey();
                List<Candle> candles = entry.getValue();
                String stats = String.format("%d свечей (%s-%s)", 
                        candles.size(),
                        formatTimestamp(candles.get(0).getTimestamp()),
                        formatTimestamp(candles.get(candles.size() - 1).getTimestamp()));
                tickerStats.put(ticker, stats);
            }
            
            // Логируем статистику каждого валидного тикера
            log.info("📊 СТАТИСТИКА ВАЛИДНЫХ ТИКЕРОВ:");
            for (Map.Entry<String, String> entry : tickerStats.entrySet()) {
                log.info("   ✅ {}: {}", entry.getKey(), entry.getValue());
            }
            
            // Проверяем есть ли различия между валидными тикерами
            Set<Integer> candleCounts = validCandlesMap.values().stream()
                    .mapToInt(List::size)
                    .boxed()
                    .collect(Collectors.toSet());
                    
            if (candleCounts.size() > 1) {
                log.error("💥 КРИТИЧЕСКАЯ ОШИБКА ВАЛИДАЦИИ: Валидные тикеры имеют РАЗНОЕ количество свечей: {}", candleCounts);
                log.error("💥 ЭТО НЕ ДОЛЖНО ПРОИСХОДИТЬ! Все валидные тикеры должны иметь одинаковые параметры!");
                
                // Детальная диагностика проблемных тикеров
                Map<Integer, List<String>> sizeGroups = new HashMap<>();
                for (Map.Entry<String, List<Candle>> entry : validCandlesMap.entrySet()) {
                    int size = entry.getValue().size();
                    sizeGroups.computeIfAbsent(size, k -> new ArrayList<>()).add(entry.getKey());
                }
                
                log.error("💥 ГРУППИРОВКА ПО РАЗМЕРАМ:");
                for (Map.Entry<Integer, List<String>> group : sizeGroups.entrySet()) {
                    log.error("   {} свечей: {}", group.getKey(), String.join(", ", group.getValue()));
                }
            }
        }
        
        log.info("🔄 ФИЛЬТРАЦИЯ: Возвращаем {} валидных тикеров из {} исходных",
                validCandlesMap.size(), candlesMap.size());
        
        if (!invalidTickers.isEmpty()) {
            log.warn("🗑️ ИСКЛЮЧЕНЫ: {} тикеров - {}", 
                    invalidTickers.size(), 
                    invalidTickers.size() <= 5 ? 
                    String.join(", ", invalidTickers) : 
                    String.join(", ", invalidTickers.subList(0, 5)) + "...");
        }
        
        return validCandlesMap; // ✅ Возвращаем только валидные данные
    }
    
    /**
     * 🔄 ВАЛИДАЦИЯ С ПОВТОРНОЙ ПОПЫТКОЙ ДОГРУЗКИ
     * Проверяет данные, догружает недостающие свечи для невалидных тикеров,
     * затем повторно валидирует и исключает окончательно невалидные тикеры
     */
    private Map<String, List<Candle>> validateAndFilterCandlesWithRetry(
            Map<String, List<Candle>> candlesMap, String timeframe, int candleLimit, 
            List<String> originalTickers, String exchange) {
        
        log.info("🔍 ПЕРВИЧНАЯ ВАЛИДАЦИЯ: Проверяем {} тикеров перед возможной догрузкой", 
                candlesMap.size());
        
        // Первичная валидация
        Map<String, List<Candle>> validCandlesMap = validateAndFilterCandlesConsistency(
                candlesMap, timeframe, candleLimit);
        
        int validCount = validCandlesMap.size();
        int invalidCount = candlesMap.size() - validCount;
        
        if (invalidCount == 0) {
            log.info("✨ ВСЕ ТИКЕРЫ ВАЛИДНЫ: Догрузка не требуется, возвращаем {} тикеров", validCount);
            return validCandlesMap;
        }
        
        log.warn("⚠️ НАЙДЕНЫ НЕВАЛИДНЫЕ ТИКЕРЫ: {} из {} требуют догрузки", invalidCount, candlesMap.size());
        
        // Определяем эталонные параметры из BTC-USDT-SWAP
        String btcTicker = "BTC-USDT-SWAP";
        List<Candle> btcCandles = candlesMap.get(btcTicker);
        
        if (btcCandles == null || btcCandles.isEmpty()) {
            log.error("❌ BTC-USDT-SWAP не найден для определения эталона, возвращаем только валидные тикеры");
            return validCandlesMap;
        }
        
        btcCandles.sort(Comparator.comparingLong(Candle::getTimestamp));
        int expectedCount = btcCandles.size();
        long expectedFirstTimestamp = btcCandles.get(0).getTimestamp();
        long expectedLastTimestamp = btcCandles.get(btcCandles.size() - 1).getTimestamp();
        
        log.info("🎯 ЭТАЛОН ДОГРУЗКИ: {} свечей, {} - {}", 
                expectedCount, 
                formatTimestamp(expectedFirstTimestamp), 
                formatTimestamp(expectedLastTimestamp));
        
        // Собираем список тикеров для догрузки
        List<String> tickersToReload = new ArrayList<>();
        for (String ticker : originalTickers) {
            if (!validCandlesMap.containsKey(ticker)) {
                tickersToReload.add(ticker);
            }
        }
        
        if (tickersToReload.isEmpty()) {
            log.warn("⚠️ НЕТ ТИКЕРОВ ДЛЯ ДОГРУЗКИ: Возвращаем {} валидных тикеров", validCount);
            return validCandlesMap;
        }
        
        log.info("🔄 ДОГРУЗКА НАЧАТА: Пытаемся догрузить {} невалидных тикеров", tickersToReload.size());
        
        try {
            // Принудительная догрузка с точными параметрами эталона
            Map<String, Object> reloadResult = loadMissingCandlesForTickers(
                    tickersToReload, timeframe, candleLimit, exchange, 
                    expectedFirstTimestamp, expectedLastTimestamp);
            
            @SuppressWarnings("unchecked")
            Map<String, List<Candle>> reloadedCandles = 
                    (Map<String, List<Candle>>) reloadResult.get("candlesMap");
            Integer addedCount = (Integer) reloadResult.get("addedCount");
            
            log.info("📥 ДОГРУЗКА ЗАВЕРШЕНА: Получено {} тикеров, добавлено {} свечей в БД", 
                    reloadedCandles.size(), addedCount);
            
            // Объединяем изначально валидные данные с догруженными
            Map<String, List<Candle>> combinedMap = new ConcurrentHashMap<>(validCandlesMap);
            combinedMap.putAll(reloadedCandles);
            
            log.info("🔍 ПОВТОРНАЯ ВАЛИДАЦИЯ: Проверяем {} тикеров после догрузки", 
                    combinedMap.size());
            
            // Повторная финальная валидация
            Map<String, List<Candle>> finalValidMap = validateAndFilterCandlesConsistency(
                    combinedMap, timeframe, candleLimit);
            
            int finalValidCount = finalValidMap.size();
            int finalInvalidCount = combinedMap.size() - finalValidCount;
            
            if (finalInvalidCount > 0) {
                log.warn("🗑️ ФИНАЛЬНОЕ ИСКЛЮЧЕНИЕ: {} тикеров остались невалидными после догрузки", 
                        finalInvalidCount);
            }
            
            log.info("✅ ФИНАЛЬНЫЙ РЕЗУЛЬТАТ: {} валидных тикеров из {} исходных", 
                    finalValidCount, originalTickers.size());
            
            return finalValidMap;
            
        } catch (Exception e) {
            log.error("💥 ОШИБКА ДОГРУЗКИ: {}, возвращаем только изначально валидные {} тикеров", 
                    e.getMessage(), validCount);
            return validCandlesMap;
        }
    }
    
    /**
     * 📥 ДОГРУЗКА КОНКРЕТНЫХ ТИКЕРОВ с точными временными параметрами
     * Специализированный метод для догрузки тикеров с заданными timestamp'ами
     */
    private Map<String, Object> loadMissingCandlesForTickers(
            List<String> tickers, String timeframe, int candleLimit, String exchange,
            long expectedFirstTimestamp, long expectedLastTimestamp) {
        
        Map<String, List<Candle>> result = new ConcurrentHashMap<>();
        final int[] totalAddedCount = {0};
        
        log.info("🎯 СПЕЦИАЛЬНАЯ ДОГРУЗКА: {} тикеров с точными параметрами {}-{}", 
                tickers.size(), formatTimestamp(expectedFirstTimestamp), formatTimestamp(expectedLastTimestamp));
        
        try {
            // Многопоточная догрузка в 5 потоков
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (String ticker : tickers) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        log.info("🔄 ПОТОК: Специальная догрузка {} с точными параметрами", ticker);
                        
                        // Загружаем точное количество свечей с заданным временем
                        List<Candle> freshCandles = okxFeignClient.getCandles(
                                ticker, timeframe, candleLimit);
                        
                        if (freshCandles != null && !freshCandles.isEmpty()) {
                            // Фильтруем свечи по точному временному диапазону
                            freshCandles.sort(Comparator.comparingLong(Candle::getTimestamp));
                            
                            List<Candle> filteredCandles = freshCandles.stream()
                                    .filter(candle -> candle.getTimestamp() >= expectedFirstTimestamp && 
                                                    candle.getTimestamp() <= expectedLastTimestamp)
                                    .collect(Collectors.toList());
                            
                            log.info("📥 ПОТОК: {} - получено {} свечей, отфильтровано {} под эталон", 
                                    ticker, freshCandles.size(), filteredCandles.size());
                            
                            if (!filteredCandles.isEmpty()) {
                                result.put(ticker, filteredCandles);
                                
                                // Сохраняем в БД
                                int savedCount = candleTransactionService.saveCandlesToCache(
                                        ticker, timeframe, exchange, filteredCandles);
                                synchronized (totalAddedCount) {
                                    totalAddedCount[0] += savedCount;
                                }
                                
                                log.info("💾 ПОТОК: {} - сохранено {} свечей в БД", ticker, savedCount);
                            } else {
                                log.warn("⚠️ ПОТОК: {} - нет свечей в требуемом диапазоне", ticker);
                            }
                        } else {
                            log.warn("⚠️ ПОТОК: {} - не удалось получить свечи", ticker);
                        }
                        
                    } catch (Exception e) {
                        log.error("💥 ПОТОК: Ошибка специальной догрузки {}: {}", ticker, e.getMessage());
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            // Ждем завершения всех потоков
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            log.info("🏁 СПЕЦИАЛЬНАЯ ДОГРУЗКА: Завершена для {} тикеров, добавлено {} свечей в БД",
                    tickers.size(), totalAddedCount[0]);
            
        } catch (Exception e) {
            log.error("💥 ОШИБКА СПЕЦИАЛЬНОЙ ДОГРУЗКИ: {}", e.getMessage());
        }
        
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("candlesMap", result);
        resultMap.put("addedCount", totalAddedCount[0]);
        
        return resultMap;
    }
    
    /**
     * Форматирует timestamp для лучшей читаемости в логах
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "неизвестно";
        
        try {
            // Проверяем формат timestamp: если слишком большой, то в миллисекундах, иначе в секундах
            if (timestamp > 9999999999L) { // больше чем 2001 год в секундах
                return java.time.Instant.ofEpochMilli(timestamp).toString();
            } else {
                return java.time.Instant.ofEpochSecond(timestamp).toString();
            }
        } catch (Exception e) {
            return "ошибка_формата";
        }
    }

    public Map<String, List<Candle>> getCachedCandles(List<String> tickers, String timeframe,
                                                      int candleLimit) {
        return getCachedCandles(tickers, timeframe, candleLimit, defaultExchange);
    }

    /**
     * ПРОСТОЙ запрос свечей БЕЗ ВАЛИДАЦИИ - возвращает данные как есть
     * Используется для конкретных пар когда не нужна фильтрация
     */
    public Map<String, List<Candle>> getCachedCandlesSimple(List<String> tickers, String timeframe,
                                                            int candleLimit, String exchange) {
        log.info("🚫 ПРОСТОЙ запрос свечей БЕЗ ВАЛИДАЦИИ: {} тикеров, таймфрейм {}, лимит {}",
                tickers.size(), timeframe, candleLimit);

        Map<String, List<Candle>> result = new ConcurrentHashMap<>();

        for (String ticker : tickers) {
            List<CachedCandle> latestCandles = cachedCandleRepository
                    .findLatestByTickerTimeframeExchange(ticker, timeframe, exchange, 
                            PageRequest.of(0, candleLimit));

            if (!latestCandles.isEmpty()) {
                List<Candle> candlesList = latestCandles.stream()
                        .map(CachedCandle::toCandle)
                        .sorted(Comparator.comparing(Candle::getTimestamp))
                        .collect(Collectors.toList());
                        
                result.put(ticker, candlesList);
                log.debug("✅ ПРОСТОЙ: {} - получено {} свечей", ticker, candlesList.size());
            } else {
                log.warn("⚠️ ПРОСТОЙ: {} - нет данных в кэше", ticker);
            }
        }
        
        log.info("✅ ПРОСТОЙ запрос завершен: получено {} тикеров из {}", result.size(), tickers.size());
        return result;
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
                    
                    // КРИТИЧНО: Загружаем большие объемы по чанкам во избежание OutOfMemoryError
                    int chunkSize = getMaxLoadLimitForTimeframe(timeframe);
                    if (missing > chunkSize) {
                        // Загружаем итеративно по частям
                        log.info("📦 CHUNKED LOAD: {} - требуется догрузить {} свечей, загрузим по {} за раз", 
                                ticker, missing, chunkSize);
                        loadCandlesInChunks(ticker, timeframe, missing, chunkSize);
                        // После загрузки по чанкам, данные уже в БД
                        missingCandlesCount.put(ticker, 0); // Помечаем как обработанный
                    } else {
                        // Обычная загрузка для небольших объемов
                        missingCandlesCount.put(ticker, missing);
                        if (debugCount <= 5) {
                            log.info("🔄 Кэш PARTIAL: {} - есть {}, нужно {}, догрузим {}", 
                                    ticker, latestCandles.size(), candleLimit, missing);
                        }
                    }
                } else {
                    if (debugCount <= 5) {
                        log.info("✅ Кэш HIT: {} - {} свечей из кэша (полное покрытие)", ticker, latestCandles.size());
                    }
                }
            } else {
                // Нет данных в кэше - загружаем полное количество по чанкам
                int chunkSize = getMaxLoadLimitForTimeframe(timeframe);
                if (candleLimit > chunkSize) {
                    // Загружаем полный объем по частям
                    log.info("📦 CHUNKED LOAD: {} - нет данных в кэше, загрузим {} свечей по {} за раз", 
                            ticker, candleLimit, chunkSize);
                    loadCandlesInChunks(ticker, timeframe, candleLimit, chunkSize);
                    // После загрузки по чанкам, данные уже в БД
                    missingCandlesCount.put(ticker, 0); // Помечаем как обработанный
                } else {
                    // Обычная загрузка для небольших объемов
                    missingCandlesCount.put(ticker, candleLimit);
                    log.info("❌ Кэш MISS: {} - нет данных, загрузим {} свечей", ticker, candleLimit);
                }
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

        // 🔍 ВАЛИДАЦИЯ И ФИЛЬТРАЦИЯ СВЕЧЕЙ для коинтеграции
        return validateAndFilterCandlesWithRetry(result, timeframe, candleLimit, tickers, exchange);
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
            
            // Вычисляем начало и конец текущего дня
            java.time.LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
            java.time.LocalDateTime startOfNextDay = startOfDay.plusDays(1);
            
            List<Object[]> todayStats = cachedCandleRepository.getTodayCacheStatistics(startOfDay, startOfNextDay);
            
            Map<String, Map<String, Long>> exchangeStats = new HashMap<>();
            Map<String, Map<String, Long>> exchangeTodayStats = new HashMap<>();

            // Обрабатываем общую статистику
            for (Object[] row : rawStats) {
                String ex = (String) row[0];
                String tf = (String) row[1];
                Long count = (Long) row[2];

                exchangeStats.computeIfAbsent(ex, k -> new HashMap<>()).put(tf, count);
            }

            // Обрабатываем статистику за сегодня
            for (Object[] row : todayStats) {
                String ex = (String) row[0];
                String tf = (String) row[1];
                Long todayCount = (Long) row[2];

                exchangeTodayStats.computeIfAbsent(ex, k -> new HashMap<>()).put(tf, todayCount);
            }

            stats.put("byExchange", exchangeStats);
            stats.put("todayByExchange", exchangeTodayStats);

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
            // Фильтруем тикеры с missingCount = 0 (уже обработанные через chunked loading)
            Map<String, Integer> filteredMissingCount = missingCandlesCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (existing, replacement) -> existing,
                    ConcurrentHashMap::new
                ));

            if (filteredMissingCount.size() != missingCandlesCount.size()) {
                int skippedCount = missingCandlesCount.size() - filteredMissingCount.size();
                log.info("🚫 ФИЛЬТРАЦИЯ: Пропускаем {} тикеров с missingCount = 0 (уже обработаны через chunked loading)", 
                         skippedCount);
            }

            if (filteredMissingCount.isEmpty()) {
                log.info("✅ ВСЕ ТИКЕРЫ УЖЕ ОБРАБОТАНЫ: Нет тикеров для загрузки после фильтрации");
                Map<String, Object> emptyResult = new HashMap<>();
                emptyResult.put("candles", new ConcurrentHashMap<String, List<Candle>>());
                emptyResult.put("addedCount", 0);
                return emptyResult;
            }
            
            log.info("🚀 МНОГОПОТОЧНАЯ догрузка недостающих свечей в 5 потоков для {} тикеров", 
                    filteredMissingCount.size());

            // ИСПРАВЛЕНО: Многопоточная загрузка недостающих данных в 5 потоков
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (Map.Entry<String, Integer> entry : filteredMissingCount.entrySet()) {
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
     * Загружает большой объем свечей по частям (чанкам) для избежания OutOfMemoryError
     */
    private void loadCandlesInChunks(String ticker, String timeframe, int totalMissing, int chunkSize) {
        try {
            int loadedSoFar = 0;
            int totalChunks = (int) Math.ceil((double) totalMissing / chunkSize);
            
            log.info("🚀 НАЧАЛО CHUNKED LOAD: {} - загрузим {} свечей за {} чанков по {} свечей", 
                    ticker, totalMissing, totalChunks, chunkSize);
            
            for (int chunkNum = 1; chunkNum <= totalChunks; chunkNum++) {
                int remainingToLoad = totalMissing - loadedSoFar;
                int currentChunkSize = Math.min(chunkSize, remainingToLoad);
                
                log.info("📦 CHUNK {}/{}: {} - загружаем {} свечей (загружено: {}/{})", 
                        chunkNum, totalChunks, ticker, currentChunkSize, loadedSoFar, totalMissing);
                
                // Загружаем чанк и сохраняем в БД сразу
                int actuallyLoaded = loadCandlesChunkOptimized(ticker, timeframe, currentChunkSize);
                loadedSoFar += actuallyLoaded;
                
                // Принудительная очистка памяти после каждого чанка
                System.gc();
                
                // Пауза между чанками для снижения нагрузки
                Thread.sleep(500); // Уменьшил паузу для ускорения
                
                log.info("✅ CHUNK {}/{} ЗАВЕРШЕН: {} - загружено {} свечей (прогресс: {}/{})", 
                        chunkNum, totalChunks, ticker, actuallyLoaded, loadedSoFar, totalMissing);
                
                // Если получили меньше данных чем ожидали - прерываем
                if (actuallyLoaded < Math.min(currentChunkSize, 1000)) { // Учитываем что данных может не хватать
                    log.warn("⚠️ CHUNK INCOMPLETE: {} - получено {} из {} свечей, завершаем загрузку", 
                            ticker, actuallyLoaded, currentChunkSize);
                    break;
                }
            }
            
            log.info("🎉 CHUNKED LOAD ЗАВЕРШЕН: {} - итого загружено {} из {} запрошенных свечей", 
                    ticker, loadedSoFar, totalMissing);
            
        } catch (Exception e) {
            log.error("❌ Ошибка при загрузке чанками для {}: {}", ticker, e.getMessage(), e);
        }
    }

    /**
     * Оптимизированная загрузка чанка с правильным подсчетом загруженных свечей
     */
    private int loadCandlesChunkOptimized(String ticker, String timeframe, int chunkSize) {
        int loadedCount = 0;
        int batchSize = 300; // OKX API лимит
        Long beforeTimestamp = null;

        try {
            // Получаем СТАРЕЙШУЮ временную метку из кэша для загрузки исторических данных
            Optional<Long> minTimestamp = cachedCandleRepository
                .findMinTimestampByTickerTimeframeExchange(ticker, timeframe, "OKX");
            if (minTimestamp.isPresent()) {
                beforeTimestamp = minTimestamp.get();
                log.info("🔄 CHUNK START: {} - начинаем с самой старой свечи timestamp={}", 
                        ticker, beforeTimestamp);
            } else {
                log.info("🔄 CHUNK START: {} - нет данных в кэше, загружаем с текущего момента", ticker);
            }
            
            while (loadedCount < chunkSize) {
                int remainingCandles = chunkSize - loadedCount;
                int currentBatchSize = Math.min(batchSize, remainingCandles);

                List<Candle> batchCandles;
                if (beforeTimestamp == null) {
                    batchCandles = okxFeignClient.getCandles(ticker, timeframe, currentBatchSize);
                    log.info("🌐 API CALL: {} - getCandles(size={})", ticker, currentBatchSize);
                } else {
                    batchCandles = okxFeignClient.getCandlesBefore(ticker, timeframe, currentBatchSize, beforeTimestamp);
                    log.info("🌐 API CALL: {} - getCandlesBefore(size={}, before={})", 
                            ticker, currentBatchSize, beforeTimestamp);
                }

                if (batchCandles == null || batchCandles.isEmpty()) {
                    log.warn("⚠️ NO DATA: {} - получены пустые данные, прерываем загрузку", ticker);
                    break; // Больше нет данных
                }

                // Логируем диапазон загруженных свечей
                long firstTimestamp = batchCandles.get(0).getTimestamp();
                long lastTimestamp = batchCandles.get(batchCandles.size() - 1).getTimestamp();
                log.info("📥 BATCH RECEIVED: {} - {} свечей [{}...{}]", 
                        ticker, batchCandles.size(), firstTimestamp, lastTimestamp);

                // Сохраняем батч в БД сразу
                candleTransactionService.saveCandlesToCache(ticker, timeframe, "OKX", batchCandles);
                loadedCount += batchCandles.size();

                // Обновляем timestamp для следующего запроса (берем самую старую из текущего батча)
                beforeTimestamp = batchCandles.get(0).getTimestamp(); // ИСПРАВЛЕНО: берем первую (самую старую) свечу
                log.info("🔄 NEXT TIMESTAMP: {} - следующий запрос before={} (самая старая из текущего батча)", 
                        ticker, beforeTimestamp);

                // Короткая пауза между батчами
                Thread.sleep(120);

                if (loadedCount % 1500 == 0) { // Каждые 5 батчей
                    log.info("💾 Чанк прогресс: {} - сохранено {} свечей", ticker, loadedCount);
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка загрузки чанка для {}: {}", ticker, e.getMessage(), e);
        }

        return loadedCount; // Возвращаем реальное количество загруженных свечей
    }

    /**
     * Определяет максимальное количество свечей для догрузки во избежание OutOfMemoryError
     */
    private int getMaxLoadLimitForTimeframe(String timeframe) {
        return switch (timeframe) {
            case "1m" -> 50000;   // ~35 дней для 1m (безопасно для памяти)
            case "5m" -> 100000;  // ~347 дней для 5m
            case "15m" -> 50000;  // ~520 дней для 15m
            case "1H" -> 20000;   // ~833 дня для 1H
            case "4H" -> 10000;   // ~1667 дней для 4H
            case "1D" -> 3650;    // 10 лет для 1D
            default -> 20000;     // Безопасный дефолт
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
        int loadedCount = 0;
        int batchSize = 300; // Максимум для OKX API
        Long beforeTimestamp = null;
        List<Candle> allSavedCandles = new ArrayList<>(); // Только для возврата метаданных

        try {
            while (loadedCount < totalLimit) {
                int remainingCandles = totalLimit - loadedCount;
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

                // КРИТИЧНО: Сохраняем батч в БД СРАЗУ вместо накопления в памяти
                candleTransactionService.saveCandlesToCache(ticker, timeframe, "OKX", batchCandles);
                loadedCount += batchCandles.size();
                
                // Сохраняем только первые и последние свечи для валидации
                if (allSavedCandles.isEmpty()) {
                    allSavedCandles.addAll(batchCandles.subList(0, Math.min(10, batchCandles.size())));
                }
                if (batchCandles.size() >= 10) {
                    List<Candle> lastCandles = batchCandles.subList(Math.max(0, batchCandles.size() - 10), batchCandles.size());
                    allSavedCandles.addAll(lastCandles);
                }

                // Устанавливаем timestamp для следующего запроса
                beforeTimestamp = batchCandles.get(batchCandles.size() - 1).getTimestamp();

                // Пауза между запросами для соблюдения rate limit
                Thread.sleep(120);

                log.info("💾 Сохранен батч {} свечей для {} (всего загружено: {})",
                        batchCandles.size(), ticker, loadedCount);
                
                // Принудительно очищаем память каждые 5 батчей
                if ((loadedCount / batchSize) % 5 == 0) {
                    System.gc();
                }
            }

            // Получаем уже сохраненные данные из БД для возврата (последние 100)
            List<CachedCandle> cachedCandles = cachedCandleRepository
                .findLatestByTickerTimeframeExchange(ticker, timeframe, "OKX", PageRequest.of(0, 100));
            List<Candle> savedCandles = cachedCandles.stream()
                .map(CachedCandle::toCandle)
                .collect(Collectors.toList());
            
            log.info("✅ Загрузка завершена для {}: {} свечей сохранено в БД", ticker, loadedCount);
            
            return savedCandles; // Возвращаем только часть для экономии памяти

        } catch (Exception e) {
            log.error("❌ Ошибка пагинации свечей для {}: {}", ticker, e.getMessage(), e);
            return new ArrayList<>();
        }
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