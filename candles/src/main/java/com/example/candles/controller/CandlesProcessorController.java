package com.example.candles.controller;

import com.example.candles.client.OkxFeignClient;
import com.example.candles.service.CacheValidatedCandlesProcessor;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import java.util.Objects;

@RestController
@RequestMapping("/api/candles-processor")
@RequiredArgsConstructor
@Slf4j
public class CandlesProcessorController {

    private static final String STANDARD_TICKER_BTC = "BTC-USDT-SWAP";
    private final CacheValidatedCandlesProcessor cacheValidatedCandlesProcessor;
//    private final CandlesLoaderProcessor candlesLoaderProcessor;
    private final OkxFeignClient okxFeignClient;

    /**
     * Получить валидированные свечи из кэша для множества тикеров (аналог /all-extended) с догрузкой и сохранением
     * <p>
     * POST /api/candles-processor/validated-cache-extended
     */
    @PostMapping("/validated-cache-extended")
    public ResponseEntity<?> getValidatedCandlesExtended(@RequestBody ExtendedCandlesRequest request) {
        log.info("");
        log.info("Получен запрос ExtendedCandlesRequest на поиск стабильных пар...");
        try {
            /*
             * БЛОК 0: СТРОГАЯ ВАЛИДАЦИЯ ВХОДНЫХ ПАРАМЕТРОВ
             * - Все обязательные поля должны быть переданы корректно
             * - Валидируем значения на корректность
             * - Предотвращаем ошибки из-за некорректных запросов
             */
            // Валидация самого запроса
            Objects.requireNonNull(request, "Request body не может быть null");
            
            // Валидация обязательных полей
            Objects.requireNonNull(request.getExchange(), "exchange обязательное поле");
            Objects.requireNonNull(request.getTimeframe(), "timeframe обязательное поле");
            Objects.requireNonNull(request.getPeriod(), "period обязательное поле");
            Objects.requireNonNull(request.getUntilDate(), "untilDate обязательное поле");
            Objects.requireNonNull(request.getUseCache(), "useCache обязательное поле");
            Objects.requireNonNull(request.getSorted(), "sorted обязательное поле");

            // Валидация значений на корректность
            if (request.getExchange().trim().isEmpty()) {
                throw new IllegalArgumentException("exchange не может быть пустым");
            }
            if (request.getTimeframe().trim().isEmpty()) {
                throw new IllegalArgumentException("timeframe не может быть пустым");
            }
            if (request.getPeriod().trim().isEmpty()) {
                throw new IllegalArgumentException("period не может быть пустым");
            }
            if (request.getUntilDate().trim().isEmpty()) {
                throw new IllegalArgumentException("untilDate не может быть пустым");
            }
            
            // Валидация minVolume должна быть уже в правильных единицах (не требует умножения)
            if (request.getMinVolume() < 0) {
                throw new IllegalArgumentException("minVolume не может быть отрицательным");
            }
            
            log.debug("✅ ВАЛИДАЦИЯ ЗАПРОСА: Все обязательные поля корректно переданы");
            
            /*
             * БЛОК 1: ПОДГОТОВКА ПАРАМЕТРОВ (БЕЗ ЗНАЧЕНИЙ ПО УМОЛЧАНИЮ)
             * - Используем строго переданные значения
             * - НЕ устанавливаем fallback значения - все должно быть передано клиентом
             */
            // Используем переданные значения БЕЗ fallback логики
            String exchange = request.getExchange();
            String timeframe = request.getTimeframe();
            String period = request.getPeriod();
            String untilDate = request.getUntilDate();
            double minVolume = request.getMinVolume(); // Уже в правильных единицах, не умножаем
            boolean useCache = request.getUseCache();

            log.info("📋 ПАРАМЕТРЫ ЗАПРОСА:");
            log.info("  📅 exchange: {}", exchange);
            log.info("  📅 timeframe: {}", timeframe);
            log.info("  📅 period: {}", period);
            log.info("  📅 untilDate: {}", untilDate);
            log.info("  📅 minVolume: {}", minVolume);
            log.info("  📅 useCache: {}", useCache);
            
            /*
             * БЛОК 2: ОПРЕДЕЛЕНИЕ СПИСКА ТИКЕРОВ
             * Логика:
             * - ЕСЛИ в запросе есть конкретные тикеры → используем их + добавляем BTC как эталон
             * - ИНАЧЕ → загружаем все доступные тикеры с OKX по минимальному объему
             */
            List<String> tickersToProcess;
            final List<String> originalRequestedTickers; // Сохраняем оригинальный список для фильтрации результата
            boolean isStandardTickerBtcAdded = false;

            if (request.getTickers() != null && !request.getTickers().isEmpty()) {
                // ПУТЬ А: Используем конкретные тикеры из запроса
                log.info("📝 Используем переданный список из {} тикеров", request.getTickers().size());
                originalRequestedTickers = new ArrayList<>(request.getTickers()); // Сохраняем оригинальный список
                tickersToProcess = new ArrayList<>(request.getTickers());

                // Добавляем BTC-USDT-SWAP как эталон если его нет в списке
                if (!tickersToProcess.contains(STANDARD_TICKER_BTC)) {
                    tickersToProcess.add(STANDARD_TICKER_BTC);
                    isStandardTickerBtcAdded = true;
                    log.info("🎯 Добавлен {} как эталон для валидации (всего {} тикеров для загрузки)", STANDARD_TICKER_BTC, tickersToProcess.size());
                }
            } else {
                // ПУТЬ Б: Загружаем все доступные тикеры с OKX
                log.info("🌐 Получаем тикеры...");
                originalRequestedTickers = null; // При загрузке всех тикеров фильтрация не нужна

                try {
                    // Получаем тикеры с повторными попытками (retry логика)
                    tickersToProcess = retryOperation(() -> okxFeignClient.getValidTickersByVolume(minVolume, request.getSorted()),
                                                    "получение тикеров от OKX API", 3);
                    log.info("Получено валидных тикеров {}", tickersToProcess.size());
                } catch (Exception e) {
                    log.error("❌ СЕТЕВАЯ ОШИБКА: Не удалось получить тикеры от OKX API после повторных попыток: {}", e.getMessage());
                    return ResponseEntity.status(503).body(Map.of("error", "Сервис временно недоступен - проблемы с получением тикеров"));
                }

                // Исключаем тикеры из excludeTickers если они указаны
                if (request.getExcludeTickers() != null && !request.getExcludeTickers().isEmpty()) {
                    log.debug("❌ Исключаем {} тикеров из результата", request.getExcludeTickers().size());
                    tickersToProcess = tickersToProcess.stream()
                            .filter(ticker -> !request.getExcludeTickers().contains(ticker))
                            .toList();
                    log.debug("✅ После исключения осталось {} тикеров", tickersToProcess.size());
                }
            }

            // Проверяем что есть тикеры для обработки
            if (tickersToProcess == null || tickersToProcess.isEmpty()) {
                log.error("❌ API ОШИБКА: Список тикеров не может быть пустым для extended запроса");
                return ResponseEntity.badRequest().body(Map.of());
            }

            /*
             * БЛОК 3: МНОГОПОТОЧНАЯ ОБРАБОТКА ТИКЕРОВ  
             * Алгоритм:
             * 1. Создаем пул из 5 потоков максимум
             * 2. Для каждого тикера запускаем задачу: 
             *    - Вызываем CacheValidatedCandlesProcessor.getValidatedCandlesFromCache()
             *    - ЕСЛИ получили свечи → добавляем в результат
             *    - ЕСЛИ пустой результат → пропускаем тикер (неактивный/новый)
             * 3. Ждем завершения всех задач (максимум 5 минут)
             */
            // Подготавливаем thread-safe коллекции для результатов
            Map<String, List<Candle>> result = new ConcurrentHashMap<>();
            AtomicInteger totalCandlesCount = new AtomicInteger(0);
            AtomicInteger processedTickers = new AtomicInteger(0);
            AtomicInteger successfulTickers = new AtomicInteger(0);

            log.debug("🚀 МНОГОПОТОЧНОСТЬ: Запускаем обработку {} тикеров в {} потоках",
                    tickersToProcess.size(), Math.min(5, tickersToProcess.size()));

            // Создаем пул потоков (максимум 5 потоков)
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(5, tickersToProcess.size()));
            try {
                // Создаем задачи для каждого тикера
                List<Future<Void>> futures = new ArrayList<>();

                for (String ticker : tickersToProcess) {
                    List<String> finalTickersToProcess = tickersToProcess;
                    Future<Void> future = executor.submit(() -> {
                        /*
                         * ЗАДАЧА ПОТОКА: ОБРАБОТКА ОДНОГО ТИКЕРА
                         * 1. Вызываем CacheValidatedCandlesProcessor → получаем валидированные свечи из кэша
                         * 2. Процессор внутри делает:
                         *    - Проверка кэша
                         *    - Валидация по количеству и консистентности
                         *    - При необходимости - догрузка с OKX (максимум 2 попытки)
                         *    - Возврат валидных свечей ИЛИ пустого списка (если тикер проблемный)
                         * 3. Добавляем результат в общую коллекцию
                         */
                        int tickerNumber = processedTickers.incrementAndGet();
                        String threadName = Thread.currentThread().getName();

                        log.debug("🔄 [{}/{}] Поток {}: Обрабатываем тикер {}",
                                tickerNumber, finalTickersToProcess.size(), threadName, ticker);

                        try {
                            long startTime = System.currentTimeMillis();

                            // ОСНОВНОЙ ВЫЗОВ: получение валидированных свечей из кэша с автоматической догрузкой
                            List<Candle> candles = cacheValidatedCandlesProcessor.getValidatedCandlesFromCache(
                                    exchange, ticker, untilDate, timeframe, period);

                            long duration = System.currentTimeMillis() - startTime;

                            if (!candles.isEmpty()) {
                                // УСПЕХ: добавляем тикер в результат
                                result.put(ticker, candles);
                                totalCandlesCount.addAndGet(candles.size());
                                successfulTickers.incrementAndGet();
                                log.debug("✅ [{}/{}] Поток {}: Получено {} свечей для тикера {} за {} мс",
                                        tickerNumber, finalTickersToProcess.size(), threadName, candles.size(), ticker, duration);
                            } else {
                                // ПРОПУСК: тикер не прошел валидацию (неактивный/новый/делистинг)
                                log.debug("⚠️ [{}/{}] Поток {}: Пустой результат для тикера {} - возможно неактивный/делистингованный тикер за {} мс",
                                        tickerNumber, finalTickersToProcess.size(), threadName, ticker, duration);
                            }
                        } catch (Exception e) {
                            // ОШИБКА: логируем и пропускаем тикер
                            log.error("❌ [{}/{}] Поток {}: Ошибка при получении свечей для тикера {}: {} - пропускаем тикер",
                                    tickerNumber, finalTickersToProcess.size(), threadName, ticker, e.getMessage());
                            // НЕ прерываем обработку - просто пропускаем проблемный тикер
                        }

                        return null;
                    });

                    futures.add(future);
                }

                /*
                 * ОЖИДАНИЕ ЗАВЕРШЕНИЯ ВСЕХ ПОТОКОВ
                 * - Максимум 5 минут на обработку всех тикеров
                 * - Принудительное завершение при превышении таймаута
                 */
                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                    log.warn("⚠️ ТАЙМАУТ: Некоторые задачи не завершились за 10 минут, принудительно завершаем");
                    executor.shutdownNow();
                    // Даем время на корректное завершение
                    try {
                        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                            log.error("❌ КРИТИЧНО: Не удалось корректно завершить все потоки");
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("❌ Прервано при ожидании завершения потоков");
                    }
                }

                // Собираем результаты выполненных задач
                for (Future<Void> future : futures) {
                    try {
                        if (future.isDone()) {
                            future.get(); // Получаем результат без таймаута для завершенных задач
                        } else {
                            log.warn("⚠️ Задача не завершена после общего таймаута");
                        }
                    } catch (ExecutionException e) {
                        log.error("❌ Критическая ошибка в задаче: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    }
                }

            } catch (InterruptedException e) {
                log.error("❌ ПРЕРЫВАНИЕ: Обработка была прервана: {}", e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                // Гарантированно закрываем executor
                if (!executor.isShutdown()) {
                    executor.shutdownNow();
                }
            }

            /*
             * БЛОК 4: ВАЛИДАЦИЯ И ФИНАЛИЗАЦИЯ РЕЗУЛЬТАТОВ
             * 1. Проверяем что получили данные
             * 2. Валидируем консистентность между тикерами (без повторной догрузки!)
             * 3. Применяем фильтрацию по оригинальному запросу
             */
            if (!result.isEmpty()) {
                int totalCandles = result.values().stream().mapToInt(List::size).sum();
                int avgCandles = totalCandles / result.size();
                log.debug("💾 Запрос ИЗ КЭША завершен! Получено {} тикеров со средним количеством {} свечей (всего {} свечей)",
                        result.size(), avgCandles, totalCandles);
            } else {
                log.warn("⚠️ Кэш не содержит данных - проверьте работу предзагрузки!");
            }

            log.debug("✅ API РЕЗУЛЬТАТ: Возвращаем {} свечей для {}/{} тикеров (обработано успешно)",
                    totalCandlesCount.get(), successfulTickers.get(), tickersToProcess.size());

            /*
             * КРИТИЧЕСКАЯ ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ С ФИЛЬТРАЦИЕЙ
             * - Находим эталонный тикер (с максимальным количеством свечей)
             * - Удаляем все тикеры, которые не соответствуют эталону по количеству и временным диапазонам
             * - Core service должен получить ТОЛЬКО 100% валидные данные
             */
            Map<String, List<Candle>> validatedResult = filterInvalidTickers(result);
            
            if (validatedResult.size() < result.size()) {
                int removedCount = result.size() - validatedResult.size();
                log.debug("🗑️ ФИЛЬТРАЦИЯ КОНСИСТЕНТНОСТИ: Удалено {} невалидных тикеров из {}, осталось {} валидных",
                        removedCount, result.size(), validatedResult.size());
            } else {
                log.debug("✅ ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ: Все {} тикеров имеют идентичные диапазоны и количество свечей", result.size());
            }

            /*
             * БЛОК 5: ФИЛЬТРАЦИЯ РЕЗУЛЬТАТОВ ПО ОРИГИНАЛЬНОМУ ЗАПРОСУ
             * Логика:
             * - ЕСЛИ были переданы конкретные тикеры → возвращаем только их (убираем BTC-эталон)
             * - ЕСЛИ загружали все тикеры → возвращаем все кроме BTC-эталона
             */
            Map<String, List<Candle>> finalResult = validatedResult;
            if (originalRequestedTickers != null) {
                // Фильтруем по оригинальному запросу (убираем BTC-эталон)
                finalResult = validatedResult.entrySet().stream()
                        .filter(entry -> originalRequestedTickers.contains(entry.getKey()))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        ));
                log.debug("🎯 Отфильтрованы результаты: возвращаем {} из {} тикеров",
                        finalResult.size(), validatedResult.size());
            } else if (isStandardTickerBtcAdded) {
                // Убираем BTC только если это загрузка всех тикеров
                finalResult = new ConcurrentHashMap<>(validatedResult);
                finalResult.remove(STANDARD_TICKER_BTC);
            }

            // Детальное логирование финального результата
            if (finalResult.isEmpty()) {
                log.warn("⚠️ ФИНАЛЬНЫЙ РЕЗУЛЬТАТ: Пустой результат - нет валидных тикеров");
            } else {
                log.info("📊 ФИНАЛЬНЫЙ РЕЗУЛЬТАТ: Возвращаем {} валидных тикеров:", finalResult.size());
                finalResult.forEach((ticker, candles) -> {
                    if (!candles.isEmpty()) {
                        long firstTimestamp = candles.get(0).getTimestamp();
                        long lastTimestamp = candles.get(candles.size() - 1).getTimestamp();
                        log.info("  ✅ {}: {} свечей, период с {} до {}", 
                            ticker, 
                            candles.size(),
                            formatTimestamp(firstTimestamp),
                            formatTimestamp(lastTimestamp));
                    } else {
                        log.warn("  ❌ {}: пустой список свечей", ticker);
                    }
                });
            }
            
            return ResponseEntity.ok(finalResult);

        } catch (IllegalArgumentException e) {
            log.error("❌ ВАЛИДАЦИЯ ОШИБКА: Некорректные параметры запроса: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректные параметры: " + e.getMessage()));
        } catch (Exception e) {
            log.error("❌ API ОШИБКА: Ошибка при получении валидированных свечей (extended): {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of());
        }
    }

//    /**
//     * Генерирует дату "до" как начало текущего дня в формате 2025-09-27T00:00:00Z
//     */
//    private String generateUntilDate() {
//        return LocalDate.now().atStartOfDay() + ":00Z";
//    }

//    /**
//     * Валидирует консистентность данных между тикерами с возможностью догрузки недостающих данных
//     */
//    private ValidationResult validateDataConsistencyBetweenTickersWithReload(
//            Map<String, List<Candle>> tickerData, String exchange, String untilDate,
//            String timeframe, String period, List<String> allTickers) {
//
//        log.info("🔍 ВАЛИДАЦИЯ С ДОГРУЗКОЙ: Проверяем {} тикеров на соответствие данных", tickerData.size());
//
//        if (tickerData.isEmpty()) {
//            return new ValidationResult(false, "Нет данных для валидации");
//        }
//
//        // Максимум 2 попытки догрузки
//        for (int attempt = 1; attempt <= 2; attempt++) {
//            log.info("🔄 ПОПЫТКА #{}: Валидация консистентности", attempt);
//
//            ValidationResult basicResult = validateDataConsistencyBetweenTickers(tickerData);
//
//            if (basicResult.isValid) {
//                log.info("✅ ВАЛИДАЦИЯ С ДОГРУЗКОЙ: Все тикеры консистентны после {} попыток", attempt);
//                return basicResult;
//            }
//
//            if (attempt == 2) {
//                log.error("❌ ВАЛИДАЦИЯ С ДОГРУЗКОЙ: Не удалось добиться консистентности после 2 попыток");
//                return basicResult; // Возвращаем последнюю ошибку
//            }
//
//            log.warn("⚠️ ПОПЫТКА #{}: Обнаружены несоответствия, запускаем догрузку", attempt);
//
//            // Находим тикеры с недостаточным количеством данных
//            List<String> tickersToReload = findTickersNeedingReload(tickerData);
//
//            if (tickersToReload.isEmpty()) {
//                log.error("❌ ДОГРУЗКА: Не удалось определить тикеры для догрузки");
//                return basicResult;
//            }
//
//            log.info("🔄 ДОГРУЗКА: Перезагружаем данные для {} тикеров: {}",
//                    tickersToReload.size(), tickersToReload);
//
//            // Догружаем данные для проблемных тикеров
//            boolean reloadSuccess = reloadDataForTickers(tickersToReload, exchange, untilDate, timeframe, period);
//
//            if (!reloadSuccess) {
//                log.error("❌ ДОГРУЗКА: Не удалось догрузить данные");
//                return new ValidationResult(false, "Не удалось догрузить недостающие данные");
//            }
//
//            // Заново получаем данные для всех тикеров
//            log.info("🔄 ПОВТОРНАЯ ЗАГРУЗКА: Заново получаем данные для всех тикеров");
//            tickerData.clear();
//            tickerData.putAll(reloadAllTickersData(allTickers, exchange, untilDate, timeframe, period));
//
//            if (tickerData.isEmpty()) {
//                log.error("❌ ПОВТОРНАЯ ЗАГРУЗКА: Не удалось получить данные");
//                return new ValidationResult(false, "Не удалось получить данные после догрузки");
//            }
//        }
//
//        return new ValidationResult(false, "Неожиданная ошибка в валидации с догрузкой");
//    }

//    /**
//     * Находит тикеры, которым нужна догрузка данных
//     */
//    private List<String> findTickersNeedingReload(Map<String, List<Candle>> tickerData) {
//        if (tickerData.isEmpty()) {
//            return List.of();
//        }
//
//        // Находим максимальное количество свечей среди всех тикеров
//        int maxCandles = tickerData.values().stream()
//                .mapToInt(List::size)
//                .max()
//                .orElse(0);
//
//        // Находим самый широкий временной диапазон (самую старую первую свечу и самую новую последнюю свечу)
//        long oldestFirstTimestamp = Long.MAX_VALUE;
//        long newestLastTimestamp = Long.MIN_VALUE;
//
//        for (List<Candle> candles : tickerData.values()) {
//            if (!candles.isEmpty()) {
//                long firstTimestamp = candles.get(0).getTimestamp();
//                long lastTimestamp = candles.get(candles.size() - 1).getTimestamp();
//                oldestFirstTimestamp = Math.min(oldestFirstTimestamp, firstTimestamp);
//                newestLastTimestamp = Math.max(newestLastTimestamp, lastTimestamp);
//            }
//        }
//
//        // Делаем переменные effectively final для использования в lambda
//        final long finalOldestFirstTimestamp = oldestFirstTimestamp;
//        final long finalNewestLastTimestamp = newestLastTimestamp;
//
//        log.debug("🔍 АНАЛИЗ: Максимальное количество свечей: {}", maxCandles);
//        log.debug("🔍 АНАЛИЗ: Эталонный диапазон: {} - {}",
//                formatTimestamp(finalOldestFirstTimestamp), formatTimestamp(finalNewestLastTimestamp));
//
//        // Возвращаем тикеры, которым нужна догрузка (меньше свечей ИЛИ неполный временной диапазон)
//        return tickerData.entrySet().stream()
//                .filter(entry -> {
//                    List<Candle> candles = entry.getValue();
//                    if (candles.isEmpty()) return true;
//
//                    // Проверяем количество свечей
//                    boolean needsMoreCandles = candles.size() < maxCandles;
//
//                    // Проверяем временной диапазон
//                    long firstTimestamp = candles.get(0).getTimestamp();
//                    long lastTimestamp = candles.get(candles.size() - 1).getTimestamp();
//                    boolean needsOlderData = firstTimestamp > finalOldestFirstTimestamp;
//                    boolean needsNewerData = lastTimestamp < finalNewestLastTimestamp;
//
//                    return needsMoreCandles || needsOlderData || needsNewerData;
//                })
//                .map(Map.Entry::getKey)
//                .peek(ticker -> {
//                    List<Candle> candles = tickerData.get(ticker);
//                    if (!candles.isEmpty()) {
//                        long firstTimestamp = candles.get(0).getTimestamp();
//                        long lastTimestamp = candles.get(candles.size() - 1).getTimestamp();
//                        log.debug(
//                                "🎯 ДОГРУЗКА НУЖНА: {} ({} свечей, диапазон {} - {})",
//                                ticker, candles.size(),
//                                formatTimestamp(firstTimestamp), formatTimestamp(lastTimestamp));
//                    } else {
//                        log.debug("🎯 ДОГРУЗКА НУЖНА: {} (нет данных)", ticker);
//                    }
//                })
//                .toList();
//    }

//    /**
//     * Догружает данные для указанных тикеров
//     */
//    private boolean reloadDataForTickers(List<String> tickers, String exchange,
//                                         String untilDate, String timeframe, String period) {
//        log.debug("🚀 ДОГРУЗКА ДАННЫХ: Запускаем догрузку для {} тикеров", tickers.size());
//
//        boolean allSuccess = true;
//        for (String ticker : tickers) {
//            try {
//                log.debug("🔄 ДОГРУЗКА: Обрабатываем тикер {}", ticker);
//                int savedCount = candlesLoaderProcessor.loadAndSaveCandles(exchange, ticker, untilDate, timeframe, period);
//
//                if (savedCount > 0) {
//                    log.debug("✅ ДОГРУЗКА: Успешно загружено {} свечей для тикера {}", savedCount, ticker);
//                } else {
//                    log.warn("⚠️ ДОГРУЗКА: Не загружено новых свечей для тикера {}", ticker);
//                    // Не считаем это критической ошибкой
//                }
//            } catch (Exception e) {
//                log.error("❌ ДОГРУЗКА: Ошибка при догрузке тикера {}: {}", ticker, e.getMessage(), e);
//                allSuccess = false;
//            }
//        }
//
//        return allSuccess;
//    }

//    /**
//     * Заново получает данные для всех тикеров после догрузки
//     */
//    private Map<String, List<Candle>> reloadAllTickersData(List<String> tickers, String exchange,
//                                                           String untilDate, String timeframe, String period) {
//        log.debug("🔄 ПЕРЕЗАГРУЗКА: Получаем свежие данные для {} тикеров", tickers.size());
//
//        Map<String, List<Candle>> result = new ConcurrentHashMap<>();
//
//        for (String ticker : tickers) {
//            try {
//                List<Candle> candles = cacheValidatedCandlesProcessor.getValidatedCandlesFromCache(
//                        exchange, ticker, untilDate, timeframe, period);
//
//                if (!candles.isEmpty()) {
//                    result.put(ticker, candles);
//                    log.debug("✅ ПЕРЕЗАГРУЗКА: Получено {} свечей для тикера {}", candles.size(), ticker);
//                } else {
//                    log.warn("⚠️ ПЕРЕЗАГРУЗКА: Пустой результат для тикера {}", ticker);
//                }
//            } catch (Exception e) {
//                log.error("❌ ПЕРЕЗАГРУЗКА: Ошибка получения данных для тикера {}: {}", ticker, e.getMessage(), e);
//            }
//        }
//
//        log.debug("✅ ПЕРЕЗАГРУЗКА: Получены данные для {}/{} тикеров", result.size(), tickers.size());
//        return result;
//    }

//    /**
//     * Валидирует консистентность данных между тикерами (базовая версия)
//     * Проверяет, что у всех тикеров одинаковые диапазоны дат и количество свечей
//     */
//    private ValidationResult validateDataConsistencyBetweenTickers(Map<String, List<Candle>> tickerData) {
//        log.debug("🔍 ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ: Проверяем {} тикеров на соответствие данных", tickerData.size());
//
//        if (tickerData.isEmpty()) {
//            return new ValidationResult(false, "Нет данных для валидации");
//        }
//
//        // Переменные для хранения эталонных значений (первого тикера)
//        String referenceTicker = null;
//        int referenceCount = -1;
//        long referenceFirstTimestamp = -1;
//        long referenceLastTimestamp = -1;
//
//        for (Map.Entry<String, List<Candle>> entry : tickerData.entrySet()) {
//            String ticker = entry.getKey();
//            List<Candle> candles = entry.getValue();
//
//            if (candles.isEmpty()) {
//                log.warn("⚠️ ВАЛИДАЦИЯ: Тикер {} имеет пустой список свечей", ticker);
//                continue;
//            }
//
//            int currentCount = candles.size();
//            long currentFirstTimestamp = candles.get(0).getTimestamp();
//            long currentLastTimestamp = candles.get(candles.size() - 1).getTimestamp();
//
//            log.debug("📊 ВАЛИДАЦИЯ: Тикер {}: {} свечей, диапазон {} - {}",
//                    ticker, currentCount,
//                    formatTimestamp(currentFirstTimestamp),
//                    formatTimestamp(currentLastTimestamp));
//
//            // Устанавливаем эталонные значения с первого тикера
//            if (referenceTicker == null) {
//                referenceTicker = ticker;
//                referenceCount = currentCount;
//                referenceFirstTimestamp = currentFirstTimestamp;
//                referenceLastTimestamp = currentLastTimestamp;
//                log.debug("🎯 ЭТАЛОН: Тикер {} установлен как эталон: {} свечей, диапазон {} - {}",
//                        referenceTicker, referenceCount,
//                        formatTimestamp(referenceFirstTimestamp),
//                        formatTimestamp(referenceLastTimestamp));
//                continue;
//            }
//
//            // Проверяем количество свечей
//            if (currentCount != referenceCount) {
//                String reason = String.format("Несоответствие количества свечей: %s имеет %d свечей, а эталон %s имеет %d свечей",
//                        ticker, currentCount, referenceTicker, referenceCount);
//                log.error("❌ ВАЛИДАЦИЯ КОЛИЧЕСТВА: {}", reason);
//                return new ValidationResult(false, reason);
//            }
//
//            // Проверяем первую свечу
//            if (currentFirstTimestamp != referenceFirstTimestamp) {
//                String reason = String.format("Несоответствие первой свечи: %s начинается с %s, а эталон %s с %s",
//                        ticker, formatTimestamp(currentFirstTimestamp),
//                        referenceTicker, formatTimestamp(referenceFirstTimestamp));
//                log.error("❌ ВАЛИДАЦИЯ ПЕРВОЙ СВЕЧИ: {}", reason);
//                return new ValidationResult(false, reason);
//            }
//
//            // Проверяем последнюю свечу
//            if (currentLastTimestamp != referenceLastTimestamp) {
//                String reason = String.format("Несоответствие последней свечи: %s заканчивается на %s, а эталон %s на %s",
//                        ticker, formatTimestamp(currentLastTimestamp),
//                        referenceTicker, formatTimestamp(referenceLastTimestamp));
//                log.error("❌ ВАЛИДАЦИЯ ПОСЛЕДНЕЙ СВЕЧИ: {}", reason);
//                return new ValidationResult(false, reason);
//            }
//
//            log.debug("✅ ВАЛИДАЦИЯ: Тикер {} соответствует эталону", ticker);
//        }
//
//        log.debug("✅ ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ: Все {} тикеров имеют идентичные диапазоны и количество свечей", tickerData.size());
//        return new ValidationResult(true, "Все тикеры имеют одинаковые диапазоны дат и количество свечей");
//    }

    /**
     * Фильтрует невалидные тикеры, оставляя только те, которые соответствуют эталону
     * по количеству свечей и временным диапазонам
     */
    private Map<String, List<Candle>> filterInvalidTickers(Map<String, List<Candle>> tickerData) {
        log.debug("🔍 ФИЛЬТРАЦИЯ НЕВАЛИДНЫХ ТИКЕРОВ: Анализируем {} тикеров", tickerData.size());
        
        if (tickerData.isEmpty()) {
            return tickerData;
        }
        
        // Находим эталонный тикер с максимальным количеством свечей
        String referenceTicker = null;
        int maxCandlesCount = 0;
        long referenceFirstTimestamp = -1;
        long referenceLastTimestamp = -1;
        
        // Первый проход: находим эталон
        for (Map.Entry<String, List<Candle>> entry : tickerData.entrySet()) {
            String ticker = entry.getKey();
            List<Candle> candles = entry.getValue();
            
            if (candles.isEmpty()) {
                continue;
            }
            
            if (candles.size() > maxCandlesCount) {
                maxCandlesCount = candles.size();
                referenceTicker = ticker;
                referenceFirstTimestamp = candles.get(0).getTimestamp();
                referenceLastTimestamp = candles.get(candles.size() - 1).getTimestamp();
            }
        }
        
        if (referenceTicker == null) {
            log.warn("⚠️ ФИЛЬТРАЦИЯ: Не найден валидный эталонный тикер");
            return tickerData;
        }
        
        log.debug("🎯 ЭТАЛОН ДЛЯ ФИЛЬТРАЦИИ: {} - {} свечей, диапазон {} - {}",
                referenceTicker, maxCandlesCount, 
                formatTimestamp(referenceFirstTimestamp),
                formatTimestamp(referenceLastTimestamp));
        
        // Второй проход: фильтруем тикеры по эталону
        Map<String, List<Candle>> validTickers = new ConcurrentHashMap<>();
        List<String> invalidTickers = new ArrayList<>();
        
        for (Map.Entry<String, List<Candle>> entry : tickerData.entrySet()) {
            String ticker = entry.getKey();
            List<Candle> candles = entry.getValue();
            
            if (candles.isEmpty()) {
                invalidTickers.add(ticker + "(пустой)");
                continue;
            }
            
            boolean isValid = true;
            List<String> issues = new ArrayList<>();
            
            // Проверка количества свечей
            if (candles.size() != maxCandlesCount) {
                isValid = false;
                issues.add("свечей:" + candles.size() + "≠" + maxCandlesCount);
            }
            
            // Проверка временных диапазонов
            long firstTimestamp = candles.get(0).getTimestamp();
            long lastTimestamp = candles.get(candles.size() - 1).getTimestamp();
            
            if (firstTimestamp != referenceFirstTimestamp) {
                isValid = false;
                issues.add("начало:" + formatTimestamp(firstTimestamp) + "≠" + formatTimestamp(referenceFirstTimestamp));
            }
            
            if (lastTimestamp != referenceLastTimestamp) {
                isValid = false;
                issues.add("конец:" + formatTimestamp(lastTimestamp) + "≠" + formatTimestamp(referenceLastTimestamp));
            }
            
            if (isValid) {
                validTickers.put(ticker, candles);
            } else {
                invalidTickers.add(ticker + "(" + String.join(", ", issues) + ")");
            }
        }
        
        log.debug("✅ ФИЛЬТРАЦИЯ РЕЗУЛЬТАТ: {} валидных тикеров из {}", validTickers.size(), tickerData.size());
        if (!invalidTickers.isEmpty()) {
            log.debug("🗑️ УДАЛЕНЫ НЕВАЛИДНЫЕ ТИКЕРЫ ({}): {}", invalidTickers.size(), String.join(", ", invalidTickers));
        }
        
        return validTickers;
    }

    /**
     * Форматирует timestamp в читаемый вид
     */
    private String formatTimestamp(long timestamp) {
        try {
            if (timestamp > 9999999999L) {
                // Миллисекунды
                return Instant.ofEpochMilli(timestamp).toString();
            } else {
                // Секунды
                return Instant.ofEpochSecond(timestamp).toString();
            }
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    /**
     * Выполняет операцию с повторными попытками в случае ошибки
     */
    private <T> T retryOperation(Supplier<T> operation, String operationName, int maxAttempts) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.debug("🔄 RETRY #{}: Выполняем операцию '{}'", attempt, operationName);
                T result = operation.get();
                if (attempt > 1) {
                    log.debug("✅ RETRY: Операция '{}' успешна после {} попыток", operationName, attempt);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("⚠️ RETRY #{}: Ошибка в операции '{}': {}", attempt, operationName, e.getMessage());
                
                if (attempt < maxAttempts) {
                    try {
                        // Экспоненциальная задержка: 1, 2, 4 секунды
                        long delay = 1000L * (1L << (attempt - 1));
                        log.info("⏳ RETRY: Ожидание {} мс перед следующей попыткой", delay);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Прервано во время ожидания retry", ie);
                    }
                }
            }
        }
        
        log.error("❌ RETRY: Операция '{}' неуспешна после {} попыток", operationName, maxAttempts);
        throw new Exception("Не удалось выполнить операцию '" + operationName + "' после " + maxAttempts + " попыток", lastException);
    }

//    /**
//     * Класс для хранения результата валидации
//     */
//    private static class ValidationResult {
//        final boolean isValid;
//        final String reason;
//
//        ValidationResult(boolean isValid, String reason) {
//            this.isValid = isValid;
//            this.reason = reason;
//        }
//    }
}