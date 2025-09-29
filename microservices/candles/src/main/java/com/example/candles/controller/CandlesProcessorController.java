package com.example.candles.controller;

import com.example.candles.service.CacheValidatedCandlesProcessor;
import com.example.candles.service.CandlesLoaderProcessor;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Контроллер для тестирования новых сервисов-процессоров
 */
@RestController
@RequestMapping("/api/candles-processor")
@RequiredArgsConstructor
@Slf4j
public class CandlesProcessorController {

    private final CacheValidatedCandlesProcessor cacheValidatedCandlesProcessor;
    private final CandlesLoaderProcessor candlesLoaderProcessor;

    /**
     * Получить валидированные свечи из кэша
     * <p>
     * GET /api/candles-processor/validated-cache?exchange=OKX&ticker=BTC-USDT-SWAP&timeframe=1H&period=1 год
     */
    @GetMapping("/validated-cache")
    public ResponseEntity<?> getValidatedCandlesFromCache(
            @RequestParam(defaultValue = "OKX") String exchange,
            @RequestParam String ticker,
            @RequestParam(defaultValue = "1H") String timeframe,
            @RequestParam(defaultValue = "1 год") String period
    ) {
        log.info("🔍 API ЗАПРОС: Получение валидированных свечей из кэша");
        log.info("📊 ПАРАМЕТРЫ: exchange={}, ticker={}, timeframe={}, period={}", exchange, ticker, timeframe, period);

        try {
            // Генерируем дату "до" как начало текущего дня
            String untilDate = generateUntilDate();
            log.info("📅 ДАТА ДО: {}", untilDate);

            // Получаем валидированные свечи
            List<Candle> candles = cacheValidatedCandlesProcessor.getValidatedCandlesFromCache(
                    exchange, ticker, untilDate, timeframe, period);

            if (candles.isEmpty()) {
                log.warn("⚠️ API РЕЗУЛЬТАТ: Не удалось получить валидированные свечи");
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Не удалось получить валидированные свечи",
                        "candlesCount", 0,
                        "candles", List.of()
                ));
            }

            log.info("✅ API РЕЗУЛЬТАТ: Возвращаем {} валидированных свечей", candles.size());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Валидированные свечи получены успешно",
                    "candlesCount", candles.size(),
                    "parameters", Map.of(
                            "exchange", exchange,
                            "ticker", ticker,
                            "timeframe", timeframe,
                            "period", period,
                            "untilDate", untilDate
                    ),
                    "timeRange", Map.of(
                            "oldest", candles.get(0).getTimestamp(),
                            "newest", candles.get(candles.size() - 1).getTimestamp()
                    ),
                    "candles", candles
            ));

        } catch (Exception e) {
            log.error("❌ API ОШИБКА: Ошибка при получении валидированных свечей: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Ошибка при получении валидированных свечей: " + e.getMessage(),
                    "candlesCount", 0
            ));
        }
    }

    /**
     * Загрузить и сохранить свечи с OKX
     * <p>
     * POST /api/candles-processor/load-and-save
     * Body: {"exchange": "OKX", "ticker": "BTC-USDT-SWAP", "timeframe": "1H", "period": "1year"}
     */
    @PostMapping("/load-and-save")
    public ResponseEntity<?> loadAndSaveCandles(@RequestBody Map<String, String> request) {
        String exchange = request.getOrDefault("exchange", "OKX");
        String ticker = request.get("ticker");
        String timeframe = request.getOrDefault("timeframe", "1H");
        String period = request.getOrDefault("period", "1year");

        log.info("🚀 API ЗАПРОС: Загрузка и сохранение свечей");
        log.info("📊 ПАРАМЕТРЫ: exchange={}, ticker={}, timeframe={}, period={}", exchange, ticker, timeframe, period);

        if (ticker == null || ticker.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Параметр ticker обязателен"
            ));
        }

        try {
            // Генерируем дату "до"
            String untilDate = generateUntilDate();
            log.info("📅 ДАТА ДО: {}", untilDate);

            // Загружаем и сохраняем свечи
            int savedCount = candlesLoaderProcessor.loadAndSaveCandles(exchange, ticker, untilDate, timeframe, period);

            if (savedCount > 0) {
                log.info("✅ API РЕЗУЛЬТАТ: Успешно загружено и сохранено {} свечей", savedCount);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Свечи успешно загружены и сохранены",
                        "savedCount", savedCount,
                        "parameters", Map.of(
                                "exchange", exchange,
                                "ticker", ticker,
                                "timeframe", timeframe,
                                "period", period,
                                "untilDate", untilDate
                        )
                ));
            } else {
                log.warn("⚠️ API РЕЗУЛЬТАТ: Не удалось загрузить свечи");
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Не удалось загрузить свечи",
                        "savedCount", 0
                ));
            }

        } catch (Exception e) {
            log.error("❌ API ОШИБКА: Ошибка при загрузке свечей: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Ошибка при загрузке свечей: " + e.getMessage(),
                    "savedCount", 0
            ));
        }
    }

    /**
     * Получить валидированные свечи из кэша для множества тикеров (аналог /all-extended)
     * <p>
     * POST /api/candles-processor/validated-cache-extended
     */
    @PostMapping("/validated-cache-extended")
    public ResponseEntity<?> getValidatedCandlesExtended(@RequestBody ExtendedCandlesRequest request) {
        log.info("🔍 API ЗАПРОС: Получение валидированных свечей из кэша (extended)");
        log.info("📊 ПАРАМЕТРЫ: timeframe={}, period={}, untilDate={}, tickers={}",
                request.getTimeframe(), request.getPeriod(), request.getUntilDate(),
                request.getTickers() != null ? request.getTickers().size() + " тикеров" : "все тикеры");

        try {
            // Устанавливаем значения по умолчанию
            String exchange = request.getExchange() != null ? request.getExchange() : "OKX";
            String timeframe = request.getTimeframe() != null ? request.getTimeframe() : "1H";
            String period = request.getPeriod() != null ? request.getPeriod() : "1 год";
            String untilDate = request.getUntilDate() != null ? request.getUntilDate() : generateUntilDate();

            log.info("📅 ДАТА ДО: {}", untilDate);

            // Определяем список тикеров для обработки
            List<String> tickersToProcess = request.getTickers();
            if (tickersToProcess == null || tickersToProcess.isEmpty()) {
                log.error("❌ API ОШИБКА: Список тикеров не может быть пустым для extended запроса");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Список тикеров не может быть пустым"
                ));
            }

            // Результат будет thread-safe
            Map<String, List<Candle>> result = new ConcurrentHashMap<>();
            AtomicInteger totalCandlesCount = new AtomicInteger(0);
            AtomicInteger processedTickers = new AtomicInteger(0);
            AtomicInteger successfulTickers = new AtomicInteger(0);

            log.info("🚀 МНОГОПОТОЧНОСТЬ: Запускаем обработку {} тикеров в {} потоках",
                    tickersToProcess.size(), Math.min(5, tickersToProcess.size()));

            // Создаем пул потоков (максимум 5 потоков)
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(5, tickersToProcess.size()));
            try {
                // Создаем задачи для каждого тикера
                List<Future<Void>> futures = new java.util.ArrayList<>();

                for (String ticker : tickersToProcess) {
                    Future<Void> future = executor.submit(() -> {
                        int tickerNumber = processedTickers.incrementAndGet();
                        String threadName = Thread.currentThread().getName();

                        log.info("🔄 [{}/{}] Поток {}: Обрабатываем тикер {}",
                                tickerNumber, tickersToProcess.size(), threadName, ticker);

                        try {
                            long startTime = System.currentTimeMillis();

                            List<Candle> candles = cacheValidatedCandlesProcessor.getValidatedCandlesFromCache(
                                    exchange, ticker, untilDate, timeframe, period);

                            long duration = System.currentTimeMillis() - startTime;

                            if (!candles.isEmpty()) {
                                result.put(ticker, candles);
                                totalCandlesCount.addAndGet(candles.size());
                                successfulTickers.incrementAndGet();
                                log.info("✅ [{}/{}] Поток {}: Получено {} свечей для тикера {} за {} мс",
                                        tickerNumber, tickersToProcess.size(), threadName, candles.size(), ticker, duration);
                            } else {
                                log.warn("⚠️ [{}/{}] Поток {}: Пустой результат для тикера {} за {} мс",
                                        tickerNumber, tickersToProcess.size(), threadName, ticker, duration);
                            }
                        } catch (Exception e) {
                            log.error("❌ [{}/{}] Поток {}: Ошибка при получении свечей для тикера {}: {}",
                                    tickerNumber, tickersToProcess.size(), threadName, ticker, e.getMessage());
                        }

                        return null;
                    });

                    futures.add(future);
                }

                // Ждем завершения всех задач (максимум 2 минуты на все тикеры)
                executor.shutdown();
                if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                    log.warn("⚠️ ТАЙМАУТ: Некоторые задачи не завершились за 1 час, принудительно завершаем");
                    executor.shutdownNow();
                }

                // Проверяем результат всех задач
                for (Future<Void> future : futures) {
                    try {
                        future.get(1, TimeUnit.SECONDS); // Короткий таймаут так как задачи уже должны быть завершены
                    } catch (TimeoutException | ExecutionException e) {
                        log.warn("⚠️ Одна из задач завершилась с ошибкой: {}", e.getMessage());
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

            log.info("✅ API РЕЗУЛЬТАТ: Возвращаем {} свечей для {}/{} тикеров (обработано успешно)",
                    totalCandlesCount.get(), successfulTickers.get(), tickersToProcess.size());

            // Валидация консистентности данных между тикерами
            ValidationResult consistencyResult = validateDataConsistencyBetweenTickers(result);
            if (!consistencyResult.isValid) {
                log.error("❌ ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ: {}", consistencyResult.reason);
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Данные не прошли валидацию консистентности: " + consistencyResult.reason,
                        "candlesCount", 0
                ));
            }

            // Для совместимости с существующим API возвращаем данные в том же формате
            // что и /all-extended: просто Map<String, List<Candle>>
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ API ОШИБКА: Ошибка при получении валидированных свечей (extended): {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Ошибка при получении валидированных свечей: " + e.getMessage(),
                    "candlesCount", 0
            ));
        }
    }

    /**
     * Генерирует дату "до" как начало текущего дня в формате 2025-09-27T00:00:00Z
     */
    private String generateUntilDate() {
        return LocalDate.now().atStartOfDay() + ":00Z";
    }

    /**
     * Валидирует консистентность данных между тикерами
     * Проверяет, что у всех тикеров одинаковые диапазоны дат и количество свечей
     */
    private ValidationResult validateDataConsistencyBetweenTickers(Map<String, List<Candle>> tickerData) {
        log.info("🔍 ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ: Проверяем {} тикеров на соответствие данных", tickerData.size());

        if (tickerData.isEmpty()) {
            return new ValidationResult(false, "Нет данных для валидации");
        }

        // Переменные для хранения эталонных значений (первого тикера)
        String referenceTicker = null;
        int referenceCount = -1;
        long referenceFirstTimestamp = -1;
        long referenceLastTimestamp = -1;

        for (Map.Entry<String, List<Candle>> entry : tickerData.entrySet()) {
            String ticker = entry.getKey();
            List<Candle> candles = entry.getValue();

            if (candles.isEmpty()) {
                log.warn("⚠️ ВАЛИДАЦИЯ: Тикер {} имеет пустой список свечей", ticker);
                continue;
            }

            int currentCount = candles.size();
            long currentFirstTimestamp = candles.get(0).getTimestamp();
            long currentLastTimestamp = candles.get(candles.size() - 1).getTimestamp();

            log.info("📊 ВАЛИДАЦИЯ: Тикер {}: {} свечей, диапазон {} - {}", 
                    ticker, currentCount, 
                    formatTimestamp(currentFirstTimestamp), 
                    formatTimestamp(currentLastTimestamp));

            // Устанавливаем эталонные значения с первого тикера
            if (referenceTicker == null) {
                referenceTicker = ticker;
                referenceCount = currentCount;
                referenceFirstTimestamp = currentFirstTimestamp;
                referenceLastTimestamp = currentLastTimestamp;
                log.info("🎯 ЭТАЛОН: Тикер {} установлен как эталон: {} свечей, диапазон {} - {}", 
                        referenceTicker, referenceCount,
                        formatTimestamp(referenceFirstTimestamp), 
                        formatTimestamp(referenceLastTimestamp));
                continue;
            }

            // Проверяем количество свечей
            if (currentCount != referenceCount) {
                String reason = String.format("Несоответствие количества свечей: %s имеет %d свечей, а эталон %s имеет %d свечей",
                        ticker, currentCount, referenceTicker, referenceCount);
                log.error("❌ ВАЛИДАЦИЯ КОЛИЧЕСТВА: {}", reason);
                return new ValidationResult(false, reason);
            }

            // Проверяем первую свечу
            if (currentFirstTimestamp != referenceFirstTimestamp) {
                String reason = String.format("Несоответствие первой свечи: %s начинается с %s, а эталон %s с %s",
                        ticker, formatTimestamp(currentFirstTimestamp), 
                        referenceTicker, formatTimestamp(referenceFirstTimestamp));
                log.error("❌ ВАЛИДАЦИЯ ПЕРВОЙ СВЕЧИ: {}", reason);
                return new ValidationResult(false, reason);
            }

            // Проверяем последнюю свечу
            if (currentLastTimestamp != referenceLastTimestamp) {
                String reason = String.format("Несоответствие последней свечи: %s заканчивается на %s, а эталон %s на %s",
                        ticker, formatTimestamp(currentLastTimestamp), 
                        referenceTicker, formatTimestamp(referenceLastTimestamp));
                log.error("❌ ВАЛИДАЦИЯ ПОСЛЕДНЕЙ СВЕЧИ: {}", reason);
                return new ValidationResult(false, reason);
            }

            log.info("✅ ВАЛИДАЦИЯ: Тикер {} соответствует эталону", ticker);
        }

        log.info("✅ ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ: Все {} тикеров имеют идентичные диапазоны и количество свечей", tickerData.size());
        return new ValidationResult(true, "Все тикеры имеют одинаковые диапазоны дат и количество свечей");
    }

    /**
     * Форматирует timestamp в читаемый вид
     */
    private String formatTimestamp(long timestamp) {
        try {
            if (timestamp > 9999999999L) {
                // Миллисекунды
                return java.time.Instant.ofEpochMilli(timestamp).toString();
            } else {
                // Секунды
                return java.time.Instant.ofEpochSecond(timestamp).toString();
            }
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    /**
     * Класс для хранения результата валидации
     */
    private static class ValidationResult {
        final boolean isValid;
        final String reason;

        ValidationResult(boolean isValid, String reason) {
            this.isValid = isValid;
            this.reason = reason;
        }
    }
}