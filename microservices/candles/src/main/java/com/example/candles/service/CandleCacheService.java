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

@Service
@RequiredArgsConstructor
@Slf4j
public class CandleCacheService {

    private final CachedCandleRepository cachedCandleRepository;
    private final OkxFeignClient okxFeignClient;
    private final CandleTransactionService candleTransactionService;

    @Value("${app.candle-cache.default-exchange:OKX}")
    private String defaultExchange;
    
    // Пул потоков для параллельной загрузки (уменьшено до 5)
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

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

        long currentTimestamp = System.currentTimeMillis() / 1000;
        long requiredFromTimestamp = calculateFromTimestamp(currentTimestamp, timeframe, candleLimit);

        // ИСПРАВЛЕНО: Проверяем что есть в кэше - берем все что есть, догружаем только отсутствующие периоды
        for (String ticker : tickers) {
            List<CachedCandle> cachedCandles = cachedCandleRepository
                    .findByTickerTimeframeExchangeFromTimestamp(ticker, timeframe, exchange, requiredFromTimestamp);

            if (!cachedCandles.isEmpty()) {
                // Всегда берем данные из кэша, даже если их меньше запрашиваемого количества
                List<Candle> candlesList = cachedCandles.stream()
                        .map(CachedCandle::toCandle)
                        .sorted(Comparator.comparing(Candle::getTimestamp))
                        .collect(Collectors.toList());
                        
                result.put(ticker, candlesList);
                
                // Проверяем нужна ли догрузка только если данных явно недостаточно
                if (cachedCandles.size() < candleLimit * 0.8) { // Догружаем только если меньше 80%
                    int missing = candleLimit - cachedCandles.size();
                    missingCandlesCount.put(ticker, missing);
                    log.debug("🔄 Кэш PARTIAL: {} - есть {} свечей, догрузим еще {}", 
                            ticker, cachedCandles.size(), missing);
                } else {
                    log.debug("✅ Кэш HIT: {} - {} свечей из кэша (достаточно)", ticker, cachedCandles.size());
                }
            } else {
                // Нет данных в кэше - нужна полная загрузка
                missingCandlesCount.put(ticker, candleLimit);
                log.debug("❌ Кэш MISS: {} - нет данных, загрузим {} свечей", ticker, candleLimit);
            }
        }

        // Загружаем недостающие свечи
        if (!missingCandlesCount.isEmpty()) {
            log.info("🔄 Догружаем {} тикеров с недостающими свечами", missingCandlesCount.size());
            Map<String, List<Candle>> missingCandles = loadMissingCandles(missingCandlesCount,
                    timeframe, exchange, requiredFromTimestamp);
            result.putAll(missingCandles);
        }

        log.info("✅ Возвращаем свечи: {} тикеров (кэш: {}, загружено: {})",
                result.size(), tickers.size() - missingCandlesCount.size(), missingCandlesCount.size());

        return result;
    }

    public void preloadAllCandles(String exchange) {
        log.info("🚀 Запуск полной предзагрузки свечей для биржи {}", exchange);

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

                preloadTimeframeForTickers(allTickers, timeframe, exchange, periodDays);

                // Небольшая пауза между таймфреймами
                Thread.sleep(1000);
            }

            log.info("✅ Полная предзагрузка завершена для биржи {}", exchange);

        } catch (Exception e) {
            log.error("❌ Ошибка при полной предзагрузке: {}", e.getMessage(), e);
        }
    }

    public void dailyCandlesUpdate(String exchange) {
        log.info("🔄 Запуск ежедневного обновления свечей для биржи {}", exchange);

        try {
            List<String> cachedTickers = cachedCandleRepository.findDistinctTickersByExchange(exchange);
            log.info("📊 Обновляем {} тикеров из кэша", cachedTickers.size());

            long currentTimestamp = System.currentTimeMillis() / 1000;
            // Обновляем последние 48 часов для безопасности
            long updateFromTimestamp = currentTimestamp - (48 * 3600);

            for (String timeframe : defaultCachePeriods.keySet()) {
                log.info("⏰ Обновление таймфрейма {} для {} тикеров", timeframe, cachedTickers.size());

                updateCandlesForTickers(cachedTickers, timeframe, exchange, updateFromTimestamp);

                Thread.sleep(500);
            }

            // ИСПРАВЛЕНО: НЕ удаляем старые свечи - держим все данные под рукой
            log.info("📚 Все исторические данные сохранены для быстрого доступа");

            log.info("✅ Ежедневное обновление завершено для биржи {}", exchange);

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

    private void preloadTimeframeForTickers(List<String> tickers, String timeframe,
                                            String exchange, int periodDays) {
        long currentTimestamp = System.currentTimeMillis() / 1000;
        int candleLimit = calculateCandleLimit(timeframe, periodDays);

        log.info("📈 МНОГОПОТОЧНАЯ предзагрузка {} свечей типа {} для {} тикеров (5 потоков)",
                candleLimit, timeframe, tickers.size());

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

                    // Сохраняем в кэш каждый тикер отдельно
                    for (Map.Entry<String, List<Candle>> entry : candlesMap.entrySet()) {
                        String ticker = entry.getKey();
                        List<Candle> candles = entry.getValue();

                        candleTransactionService.saveCandlesToCache(ticker, timeframe, exchange, candles);
                        
                        log.info("✅ ПОТОК: Сохранен {} с {} свечами", ticker, candles.size());
                    }

                    log.info("✅ ПОТОК: Обработан батч {}-{} из {} тикеров", batchIndex + 1,
                            Math.min(batchIndex + batchSize, tickers.size()), tickers.size());

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
    }

    private void updateCandlesForTickers(List<String> tickers, String timeframe,
                                         String exchange, long fromTimestamp) {
        // Аналогично preloadTimeframeForTickers, но только для недавних свечей
        int batchSize = 20;
        for (int i = 0; i < tickers.size(); i += batchSize) {
            List<String> batch = tickers.subList(i, Math.min(i + batchSize, tickers.size()));

            try {
                // Рассчитываем сколько свечей нужно для 48 часов
                int candleLimit = calculateCandleLimit(timeframe, 2); // 2 дня

                Map<String, List<Candle>> candlesMap = loadCandlesForBatch(batch, timeframe, candleLimit);

                for (Map.Entry<String, List<Candle>> entry : candlesMap.entrySet()) {
                    String ticker = entry.getKey();
                    List<Candle> candles = entry.getValue();

                    candleTransactionService.updateCandlesInCache(ticker, timeframe, exchange, candles, fromTimestamp);
                }

                Thread.sleep(100);

            } catch (Exception e) {
                log.warn("⚠️ Ошибка обновления батча: {}", e.getMessage());
            }
        }
    }

    private Map<String, List<Candle>> loadMissingCandles(Map<String, Integer> missingCandlesCount,
                                                         String timeframe, String exchange,
                                                         long requiredFromTimestamp) {
        Map<String, List<Candle>> result = new HashMap<>();

        try {
            // ИСПРАВЛЕНО: Загружаем только недостающие данные для каждого тикера индивидуально
            for (Map.Entry<String, Integer> entry : missingCandlesCount.entrySet()) {
                String ticker = entry.getKey();
                int missingCount = entry.getValue();

                try {
                    log.debug("🔄 Загружаем {} недостающих свечей для {}", missingCount, ticker);

                    // Определяем какие именно свечи отсутствуют
                    List<CachedCandle> existingCandles = cachedCandleRepository
                            .findByTickerTimeframeExchangeFromTimestamp(ticker, timeframe, exchange, requiredFromTimestamp);

                    // Если есть данные в кэше - определяем самую старую дату и загружаем данные до неё
                    List<Candle> loadedCandles;
                    if (!existingCandles.isEmpty()) {
                        // Есть данные - загружаем только старые недостающие
                        long oldestTimestamp = existingCandles.stream()
                                .mapToLong(CachedCandle::getTimestamp)
                                .min().orElse(System.currentTimeMillis() / 1000);
                        
                        log.debug("🔄 Для {} загружаем данные до {}", ticker, new java.util.Date(oldestTimestamp * 1000));
                        loadedCandles = loadCandlesWithPagination(ticker, timeframe, missingCount);
                    } else {
                        // Нет данных - загружаем полное количество
                        log.debug("🔄 Для {} загружаем полное количество {} свечей", ticker, missingCount);
                        loadedCandles = loadCandlesWithPagination(ticker, timeframe, missingCount);
                    }

                    if (!loadedCandles.isEmpty()) {
                        // Сохраняем в кэш только новые данные
                        candleTransactionService.saveCandlesToCache(ticker, timeframe, exchange, loadedCandles);

                        // Для результата объединяем с существующими данными из кэша
                        List<Candle> existingCandlesList = existingCandles.stream()
                                .map(CachedCandle::toCandle)
                                .collect(Collectors.toList());
                        
                        List<Candle> combinedCandles = new ArrayList<>(existingCandlesList);
                        combinedCandles.addAll(loadedCandles);
                        
                        // Сортируем и удаляем дубликаты по timestamp
                        List<Candle> finalCandles = combinedCandles.stream()
                                .collect(Collectors.toMap(
                                        Candle::getTimestamp,
                                        c -> c,
                                        (existing, replacement) -> existing))
                                .values()
                                .stream()
                                .sorted(Comparator.comparing(Candle::getTimestamp))
                                .collect(Collectors.toList());

                        result.put(ticker, finalCandles);
                        log.debug("✅ Для {} объединено {} свечей (было {} + загружено {})",
                                ticker, finalCandles.size(), existingCandlesList.size(), loadedCandles.size());
                    }

                } catch (Exception e) {
                    log.error("❌ Ошибка загрузки для тикера {}: {}", ticker, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка загрузки недостающих свечей: {}", e.getMessage(), e);
        }

        return result;
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

                log.debug("📥 Загружено {} свечей для {} (всего: {})",
                        batchCandles.size(), ticker, allCandles.size());
            }

            // Сортируем по времени (от старых к новым)
            allCandles.sort(Comparator.comparing(Candle::getTimestamp));

        } catch (Exception e) {
            log.error("❌ Ошибка пагинации свечей для {}: {}", ticker, e.getMessage(), e);
        }

        return allCandles;
    }
}