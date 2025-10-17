//package com.example.candles.service;
//
//import com.example.candles.client.OkxFeignClient;
//import com.example.shared.dto.Candle;
//import com.example.shared.dto.ExtendedCandlesRequest;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.stream.Collectors;
//
///**
// * Сервис для прямой загрузки свечей с OKX без использования кэша
// * Поддерживает многопоточную загрузку и корректировку временных диапазонов
// */
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class DirectCandlesService {
//
//    private final OkxFeignClient okxFeignClient;
//    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
//
//    /**
//     * Загружает свечи напрямую с OKX в 5 потоков с корректировкой временных диапазонов
//     */
//    public Map<String, List<Candle>> loadCandlesDirectly(ExtendedCandlesRequest request) {
//        log.info("🚀 ПРЯМАЯ ЗАГРУЗКА: {} свечей для таймфрейма {} С OKX",
//                request.getCandleLimit(), request.getTimeframe());
//
//        long startTime = System.currentTimeMillis();
//
//        try {
//            // Получаем список тикеров
//            List<String> swapTickers = getTickersForLoading(request);
//
//            if (swapTickers.isEmpty()) {
//                log.warn("⚠️ Список тикеров пуст - нечего загружать");
//                return Map.of();
//            }
//
//            log.info("📊 Начинаем загрузку для {} тикеров в 5 потоков", swapTickers.size());
//
//            // Корректируем количество свечей с учетом возможной длительной загрузки
//            int adjustedCandleLimit = adjustCandleLimitForDirectLoading(request);
//
//            // Разбиваем тикеры на батчи для многопоточной загрузки
//            List<List<String>> batches = createBatches(swapTickers, 5);
//
//            // Запускаем загрузку в параллельных потоках
//            List<CompletableFuture<Map<String, List<Candle>>>> futures = new ArrayList<>();
//
//            for (int i = 0; i < batches.size(); i++) {
//                final int batchIndex = i;
//                final List<String> batch = batches.get(i);
//
//                CompletableFuture<Map<String, List<Candle>>> future = CompletableFuture.supplyAsync(() ->
//                    loadBatchDirectly(batch, request.getTimeframe(), adjustedCandleLimit, batchIndex + 1)
//                , executorService);
//
//                futures.add(future);
//            }
//
//            // Собираем результаты всех потоков
//            Map<String, List<Candle>> allResults = new HashMap<>();
//            for (CompletableFuture<Map<String, List<Candle>>> future : futures) {
//                try {
//                    Map<String, List<Candle>> batchResult = future.get(3, TimeUnit.HOURS); // Максимум 3 часа на батч
//                    allResults.putAll(batchResult);
//                } catch (TimeoutException e) {
//                    log.error("❌ Таймаут загрузки батча: {}", e.getMessage());
//                } catch (Exception e) {
//                    log.error("❌ Ошибка загрузки батча: {}", e.getMessage(), e);
//                }
//            }
//
//            // Постобработка: корректировка временных диапазонов
//            Map<String, List<Candle>> finalResult = adjustTimestampsAndCount(allResults, request.getCandleLimit());
//
//            // Фильтрация результата согласно оригинальному запросу
//            Map<String, List<Candle>> filteredResult = filterResultByOriginalRequest(finalResult, request);
//
//            long elapsed = System.currentTimeMillis() - startTime;
//            logLoadingResults(filteredResult, elapsed);
//
//            return filteredResult;
//
//        } catch (Exception e) {
//            long elapsed = System.currentTimeMillis() - startTime;
//            log.error("❌ ПРЯМАЯ ЗАГРУЗКА завершилась с ошибкой за {} мс: {}", elapsed, e.getMessage(), e);
//            return Map.of();
//        }
//    }
//
//    /**
//     * Получает список тикеров для загрузки
//     */
//    private List<String> getTickersForLoading(ExtendedCandlesRequest request) {
//        List<String> swapTickers;
//
//        if (request.getTickers() != null && !request.getTickers().isEmpty()) {
//            log.info("📝 Используем переданный список из {} тикеров", request.getTickers().size());
//            swapTickers = new ArrayList<>(request.getTickers());
//
//            // Добавляем BTC-USDT-SWAP как эталон если его нет
//            if (!swapTickers.contains("BTC-USDT-SWAP")) {
//                swapTickers.add("BTC-USDT-SWAP");
//                log.info("🎯 Добавлен BTC-USDT-SWAP как эталон для валидации");
//            }
//        } else {
//            log.info("🌐 Получаем все доступные тикеры с OKX");
//            swapTickers = okxFeignClient.getAllSwapTickers(true);
//
//            // Исключаем тикеры из excludeTickers если они указаны
//            if (request.getExcludeTickers() != null && !request.getExcludeTickers().isEmpty()) {
//                log.info("❌ Исключаем {} тикеров из загрузки", request.getExcludeTickers().size());
//                swapTickers = swapTickers.stream()
//                        .filter(ticker -> !request.getExcludeTickers().contains(ticker))
//                        .toList();
//                log.info("✅ После исключения осталось {} тикеров", swapTickers.size());
//            }
//        }
//
//        return swapTickers;
//    }
//
//    /**
//     * Корректирует количество свечей с учетом возможной длительной загрузки
//     */
//    private int adjustCandleLimitForDirectLoading(ExtendedCandlesRequest request) {
//        int originalLimit = request.getCandleLimit();
//
//        // Добавляем буфер для компенсации времени загрузки
//        // Предполагаем, что загрузка может занять до 2 часов
//        int bufferCandles = calculateBufferCandles(request.getTimeframe(), 2); // 2 часа буфер
//
//        int adjustedLimit = originalLimit + bufferCandles;
//
//        if (bufferCandles > 0) {
//            log.info("⏱️ ВРЕМЕННАЯ КОРРЕКТИРОВКА: {} → {} свечей (буфер +{} для компенсации загрузки)",
//                    originalLimit, adjustedLimit, bufferCandles);
//        }
//
//        return adjustedLimit;
//    }
//
//    /**
//     * Рассчитывает буфер свечей для указанного количества часов
//     */
//    private int calculateBufferCandles(String timeframe, int hours) {
//        return switch (timeframe) {
//            case "1m" -> hours * 60;        // 60 свечей в час для 1m
//            case "5m" -> hours * 12;        // 12 свечей в час для 5m
//            case "15m" -> hours * 4;        // 4 свечи в час для 15m
//            case "1H" -> hours;             // 1 свеча в час для 1H
//            case "4H" -> hours / 4;         // 1 свеча в 4 часа для 4H
//            case "1D", "1W", "1M" -> 0;     // Для больших таймфреймов буфер не нужен
//            default -> hours;               // По умолчанию как для 1H
//        };
//    }
//
//    /**
//     * Создает батчи тикеров для многопоточной загрузки
//     */
//    private List<List<String>> createBatches(List<String> tickers, int batchCount) {
//        List<List<String>> batches = new ArrayList<>();
//        int batchSize = (int) Math.ceil((double) tickers.size() / batchCount);
//
//        for (int i = 0; i < tickers.size(); i += batchSize) {
//            int end = Math.min(i + batchSize, tickers.size());
//            batches.add(tickers.subList(i, end));
//        }
//
//        log.info("📦 Создано {} батчей для загрузки (размер батча: {})", batches.size(), batchSize);
//        return batches;
//    }
//
//    /**
//     * Загружает один батч тикеров
//     */
//    private Map<String, List<Candle>> loadBatchDirectly(List<String> tickerBatch, String timeframe, int candleLimit, int batchIndex) {
//        log.info("🔄 Поток {}: Загрузка {} тикеров с OKX...", batchIndex, tickerBatch.size());
//
//        Map<String, List<Candle>> batchResults = new HashMap<>();
//        int successCount = 0;
//        int errorCount = 0;
//
//        for (String ticker : tickerBatch) {
//            try {
//                List<Candle> candles = okxFeignClient.getCandles(ticker, timeframe, candleLimit);
//
//                if (candles != null && !candles.isEmpty()) {
//                    batchResults.put(ticker, candles);
//                    successCount++;
//
//                    // Логируем прогресс каждые 10 тикеров
//                    if (successCount % 10 == 0) {
//                        log.debug("🔄 Поток {}: Загружено {}/{} тикеров", batchIndex, successCount, tickerBatch.size());
//                    }
//                } else {
//                    log.warn("⚠️ Поток {}: Пустые данные для тикера {}", batchIndex, ticker);
//                    errorCount++;
//                }
//
//                // Небольшая пауза между запросами для снижения нагрузки на API
//                Thread.sleep(50);
//
//            } catch (Exception e) {
//                log.error("❌ Поток {}: Ошибка загрузки тикера {}: {}", batchIndex, ticker, e.getMessage());
//                errorCount++;
//
//                // Пауза при ошибке
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException ie) {
//                    Thread.currentThread().interrupt();
//                    break;
//                }
//            }
//        }
//
//        log.info("✅ Поток {} завершен: {} успешно, {} ошибок из {} тикеров",
//                batchIndex, successCount, errorCount, tickerBatch.size());
//
//        return batchResults;
//    }
//
//    /**
//     * Корректирует временные диапазоны и количество свечей после загрузки
//     */
//    private Map<String, List<Candle>> adjustTimestampsAndCount(Map<String, List<Candle>> loadedCandles, int targetCandleCount) {
//        if (loadedCandles.isEmpty()) {
//            return loadedCandles;
//        }
//
//        log.info("⏱️ КОРРЕКТИРОВКА ВРЕМЕННЫХ ДИАПАЗОНОВ: целевое количество свечей {}", targetCandleCount);
//
//        // Находим общие временные границы среди всех тикеров
//        // Для корректной синхронизации нужно найти самое позднее начало (наименее старая из старейших свечей)
//        // и самый ранний конец (наименее новая из новейших свечей)
//        long latestOldestTime = 0;  // Самая поздняя из старых свечей (пересечение начал)
//        long earliestNewestTime = Long.MAX_VALUE;  // Самая ранняя из новых свечей (пересечение концов)
//
//        for (List<Candle> candles : loadedCandles.values()) {
//            if (!candles.isEmpty()) {
//                // Свечи отсортированы по убыванию времени (новые первые)
//                long newestTime = candles.get(0).getTimestamp(); // Самая новая свеча (первая)
//                long oldestTime = candles.get(candles.size() - 1).getTimestamp(); // Самая старая свеча (последняя)
//
//                // Для синхронизации берем пересечение временных диапазонов
//                if (oldestTime > latestOldestTime) latestOldestTime = oldestTime; // Позднее начало
//                if (newestTime < earliestNewestTime) earliestNewestTime = newestTime; // Раннее окончание
//            }
//        }
//
//        log.info("🎯 Найдены временные границы для синхронизации: {} - {}",
//                Instant.ofEpochSecond(latestOldestTime), Instant.ofEpochSecond(earliestNewestTime));
//
//        // Синхронизируем временные диапазоны и обрезаем до нужного количества
//        Map<String, List<Candle>> adjustedCandles = new HashMap<>();
//        int validTickerCount = 0;
//
//        // Если временные границы некорректны, просто обрезаем без синхронизации
//        boolean shouldSynchronize = latestOldestTime > 0 && earliestNewestTime != Long.MAX_VALUE &&
//                                   earliestNewestTime >= latestOldestTime;
//
//        for (Map.Entry<String, List<Candle>> entry : loadedCandles.entrySet()) {
//            String ticker = entry.getKey();
//            List<Candle> candles = entry.getValue();
//
//            if (candles.isEmpty()) {
//                continue;
//            }
//
//            List<Candle> processedCandles;
//
//            if (shouldSynchronize) {
//                // Синхронизируем временные диапазоны - берем свечи только в общем диапазоне
//                final long finalLatestOldestTime = latestOldestTime;
//                final long finalEarliestNewestTime = earliestNewestTime;
//
//                processedCandles = candles.stream()
//                        .filter(candle -> candle.getTimestamp() >= finalLatestOldestTime &&
//                                         candle.getTimestamp() <= finalEarliestNewestTime)
//                        .limit(targetCandleCount)
//                        .toList();
//
//                log.debug("🔄 {}: синхронизировано {} свечей в диапазоне {} - {}",
//                        ticker, processedCandles.size(),
//                        Instant.ofEpochSecond(finalLatestOldestTime),
//                        Instant.ofEpochSecond(finalEarliestNewestTime));
//            } else {
//                // Простая обрезка без синхронизации
//                processedCandles = candles.stream()
//                        .limit(targetCandleCount)
//                        .toList();
//
//                log.debug("🔄 {}: обрезано до {} свечей без синхронизации", ticker, processedCandles.size());
//            }
//
//            if (!processedCandles.isEmpty()) {
//                adjustedCandles.put(ticker, processedCandles);
//                validTickerCount++;
//            }
//        }
//
//        log.info("✅ Корректировка завершена: {} валидных тикеров с {} свечами каждый",
//                validTickerCount, targetCandleCount);
//
//        return adjustedCandles;
//    }
//
//    /**
//     * Фильтрует результат согласно оригинальному запросу
//     */
//    private Map<String, List<Candle>> filterResultByOriginalRequest(Map<String, List<Candle>> allResults, ExtendedCandlesRequest request) {
//        // Если были указаны конкретные тикеры, возвращаем только их (исключая BTC эталон если он был добавлен)
//        if (request.getTickers() != null && !request.getTickers().isEmpty()) {
//            Map<String, List<Candle>> filteredResult = allResults.entrySet().stream()
//                    .filter(entry -> request.getTickers().contains(entry.getKey()))
//                    .collect(Collectors.toMap(
//                            Map.Entry::getKey,
//                            Map.Entry::getValue
//                    ));
//
//            log.info("🎯 Отфильтрованы результаты: возвращаем {} из {} тикеров",
//                    filteredResult.size(), allResults.size());
//            return filteredResult;
//        }
//
//        return allResults;
//    }
//
//    /**
//     * Логирует результаты загрузки
//     */
//    private void logLoadingResults(Map<String, List<Candle>> result, long elapsedMs) {
//        if (result != null && !result.isEmpty()) {
//            int totalCandles = result.values().stream().mapToInt(List::size).sum();
//            int avgCandles = totalCandles / result.size();
//
//            log.info("🚀 ПРЯМАЯ ЗАГРУЗКА завершена за {} мс ({} мин)! Получено {} тикеров со средним количеством {} свечей (всего {} свечей)",
//                    elapsedMs, elapsedMs / 60000, result.size(), avgCandles, totalCandles);
//
//            // Предупреждение, если загрузка заняла очень много времени
//            if (elapsedMs > 300000) { // Более 5 минут
//                log.warn("⚠️ ВНИМАНИЕ: Загрузка заняла {} минут! Рассмотрите возможность использования кэша для ускорения.",
//                        elapsedMs / 60000);
//            }
//        } else {
//            log.warn("⚠️ ПРЯМАЯ ЗАГРУЗКА завершилась за {} мс без результатов", elapsedMs);
//        }
//    }
//}