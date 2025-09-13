package com.example.okx.service;

import com.example.shared.dto.Candle;
import com.example.shared.dto.okx.OkxTickerDto;
import com.example.shared.models.Settings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class OkxClient {
    // Оптимизированный HTTP клиент с пулом соединений и таймаутами
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectionPool(new okhttp3.ConnectionPool(20, 5, TimeUnit.MINUTES))
            .build();
    private static final String BASE_URL = "https://www.okx.com";

    // Rate limiting для предотвращения блокировки OKX API
    private static final AtomicLong lastRequestTime = new AtomicLong(0);
    private static final long MIN_REQUEST_INTERVAL_MS = 120; // 120мс между запросами = 8.3 RPS (безопасно)
    private static final int BATCH_SIZE = 50; // Обрабатываем по 50 символов за раз

    // Умная задержка для rate limiting
    private void applyRateLimit() {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime.get();

        if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            long sleepTime = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest;
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastRequestTime.set(System.currentTimeMillis());
    }

    public List<String> getAllSwapTickers(boolean isSorted) {
        applyRateLimit(); // Добавляем rate limiting
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v5/public/instruments?instType=SWAP")
                .build();

        try {
            Response response = client.newCall(request).execute();
            String json = response.body().string();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = obj.getAsJsonArray("data");

            List<String> result = new ArrayList<>();
            for (JsonElement el : data) {
                String instId = el.getAsJsonObject().get("instId").getAsString();
                if (instId.endsWith("-USDT-SWAP")) {
                    result.add(instId);
                }
            }

            return isSorted ? result.stream().sorted().toList() : result;

        } catch (Exception e) {
            log.error("❌ " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public List<Double> getCloses(String symbol, String timeFrame, int limit) {
        applyRateLimit(); // Добавляем rate limiting
        JsonArray candles = getCandles(symbol, timeFrame, limit);
        List<Double> closes = new ArrayList<>();
        for (int i = candles.size() - 1; i >= 0; i--) {
            JsonArray candle = candles.get(i).getAsJsonArray();
            double close = Double.parseDouble(candle.get(4).getAsString()); //начинаем с 300 свечи
            closes.add(close);
        }
        return closes;
    }

    private JsonArray getCandles(String symbol, String timeFrame, double limit) {
        applyRateLimit(); // Применяем rate limiting

        int candlesLimit = (int) limit;
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v5/market/candles?instId=" + symbol + "&bar=" + timeFrame + "&limit=" + candlesLimit)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String json = response.body().string();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            // Проверяем успешность ответа
            JsonArray data = obj.getAsJsonArray("data");
            if (data == null || data.size() == 0) {
                log.warn("⚠️ Пустой ответ от OKX для {}", symbol);
                return new JsonArray(); // Возвращаем пустой массив вместо null
            }

            return data;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении свечей для {}: {}", symbol, e.getMessage());
            return new JsonArray(); // Возвращаем пустой массив вместо исключения
        }
    }

    public List<Candle> getCandleList(String symbol, String timeFrame, double limit) {
        int candlesLimit = (int) limit;
        JsonArray rawCandles = getCandles(symbol, timeFrame, candlesLimit);
        List<Candle> candles = new ArrayList<>();

        // Проверяем что rawCandles не null
        if (rawCandles == null || rawCandles.size() == 0) {
            log.warn("⚠️ Нет данных свечей для {}", symbol);
            return candles; // Возвращаем пустой список
        }

        for (JsonElement el : rawCandles) {
            JsonArray candleArr = el.getAsJsonArray();
            candles.add(Candle.fromJsonArray(candleArr));
        }
        Collections.reverse(candles);
        return candles;
    }

    /**
     * Получение свечей с параметром after для пагинации
     * ИСПРАВЛЕНО: after получает данные ДО указанного времени (исторические)
     */
    public List<Candle> getCandlesWithBefore(String symbol, String timeFrame, int limit, long afterTimestamp) {
        applyRateLimit();
        JsonArray rawCandles = getCandlesWithBeforeTimestamp(symbol, timeFrame, limit, afterTimestamp);
        List<Candle> candles = new ArrayList<>();

        if (rawCandles == null || rawCandles.size() == 0) {
            log.debug("⚠️ Нет исторических данных свечей для {} до {}", symbol, afterTimestamp);
            return candles;
        }

        for (JsonElement el : rawCandles) {
            JsonArray candleArr = el.getAsJsonArray();
            candles.add(Candle.fromJsonArray(candleArr));
        }
        Collections.reverse(candles);
        return candles;
    }

    /**
     * Внутренний метод для получения ИСТОРИЧЕСКИХ свечей с after timestamp
     * КРИТИЧЕСКИ ИСПРАВЛЕНО: Используем AFTER для получения данных ДО указанного времени (более старые)
     */
    private JsonArray getCandlesWithBeforeTimestamp(String symbol, String timeFrame, int limit, long afterTimestamp) {
        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Используем AFTER для исторических данных (более старые чем указанная точка)
        String url = BASE_URL + "/api/v5/market/history-candles?instId=" + symbol +
                "&bar=" + timeFrame + "&limit=" + limit + "&after=" + afterTimestamp;

        log.warn("🔍 DEBUG: OKX API запрос = {}", url);
        log.warn("🔍 DEBUG: Используем AFTER для получения данных ДО timestamp = {} ({})",
                afterTimestamp, new java.util.Date(afterTimestamp));

        Request request = new Request.Builder().url(url).build();

        try {
            Response response = client.newCall(request).execute();
            String json = response.body().string();

            log.warn("🔍 DEBUG: OKX ответ для {} = {}", symbol, json.length() > 500 ? json.substring(0, 500) + "..." : json);

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            JsonArray data = obj.getAsJsonArray("data");
            if (data == null || data.size() == 0) {
                log.debug("⚠️ Пустой ответ от OKX для {} с after={}", symbol, afterTimestamp);
                return new JsonArray();
            }

            // DEBUG: показать первую и последнюю свечу
            if (data.size() > 0) {
                JsonArray firstCandle = data.get(0).getAsJsonArray();
                JsonArray lastCandle = data.get(data.size() - 1).getAsJsonArray();
                long firstTimestamp = Long.parseLong(firstCandle.get(0).getAsString());
                long lastTimestamp = Long.parseLong(lastCandle.get(0).getAsString());

                log.warn("🔍 DEBUG: OKX вернул {} свечей. Первая: {} ({}), Последняя: {} ({})",
                        data.size(), firstTimestamp, new java.util.Date(firstTimestamp),
                        lastTimestamp, new java.util.Date(lastTimestamp));

                // Проверяем что полученные данные корректны - все должны быть ДО afterTimestamp
                if (lastTimestamp >= afterTimestamp) {
                    log.error("🚨 ПРОБЛЕМА: Последняя свеча ({}) >= after ({}). OKX вернул неверные данные!",
                            new java.util.Date(lastTimestamp), new java.util.Date(afterTimestamp));
                } else {
                    log.info("✅ УСПЕХ: Все полученные свечи ДО указанного времени {} < {}",
                            new java.util.Date(lastTimestamp), new java.util.Date(afterTimestamp));
                }
            }

            return data;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении свечей с after для {}: {}", symbol, e.getMessage());
            return new JsonArray();
        }
    }

    /**
     * Получает тикер в виде DTO
     */
    public OkxTickerDto getTickerDto(String symbol) {
        try {
            applyRateLimit(); // Добавляем rate limiting
            JsonArray tickerData = getTicker(symbol);
            return OkxTickerDto.fromJsonArray(tickerData);
        } catch (Exception e) {
            log.error("❌ Ошибка при получении DTO тикера для {}: {}", symbol, e.getMessage(), e);
            throw new RuntimeException("Ошибка при получении тикера для " + symbol, e);
        }
    }

    private JsonArray getTicker(String symbol) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/v5/market/ticker?instId=" + symbol)
                .build();

        try {
            Response response = client.newCall(request).execute();
            String json = response.body().string();
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return obj.getAsJsonArray("data");
        } catch (IOException e) {
            log.error("❌ Ошибка: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public Map<String, List<Candle>> getCandlesMap(List<String> swapTickers, Settings settings, boolean isSorted) {
        long startTime = System.currentTimeMillis();
        // Используем 5 потоков для соблюдения лимитов OKX API
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        log.debug("🔽 Запускаем загрузку свечей в {} потоков для {} тикеров (батчами по {})", threadCount, swapTickers.size(), BATCH_SIZE);
        Map<String, List<Candle>> candlesMap = Collections.synchronizedMap(new LinkedHashMap<>()); //важен порядок чтобы скрипт не менял свечи и знак z
        if (isSorted) {
            swapTickers = swapTickers.stream().sorted().toList();
        }
        int candleLimit = (int) settings.getCandleLimit();
        String timeframe = settings.getTimeframe();
        try {
            // Обрабатываем тикеры батчами для лучшего контроля rate limiting
            final List<String> tickersFinal = swapTickers;
            List<List<String>> batches = IntStream.range(0, (tickersFinal.size() + BATCH_SIZE - 1) / BATCH_SIZE)
                    .mapToObj(i -> tickersFinal.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, tickersFinal.size())))
                    .toList();

            log.debug("🔄 Обрабатываем {} батчей по {} символов", batches.size(), BATCH_SIZE);

            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                List<String> batch = batches.get(batchIndex);
                final String timeframeFinal = timeframe;
                final int candleLimitFinal = candleLimit;
                log.debug("🔄 Обрабатываем батч {}/{} ({} символов)", batchIndex + 1, batches.size(), batch.size());

                List<CompletableFuture<Void>> batchFutures = batch.stream()
                        .map(symbol -> CompletableFuture.runAsync(() -> {
                            try {
                                List<Candle> candles = getCandleList(symbol, timeframeFinal, candleLimitFinal);
                                if (candles.size() == candleLimitFinal) {
                                    synchronized (candlesMap) {
                                        candlesMap.put(symbol, candles);
                                    }
                                }
                            } catch (Exception e) {
                                log.error("❌ Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                            }
                        }, executor))
                        .toList();

                // Ожидаем завершения текущего батча
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

                // Небольшая пауза между батчами
                if (batchIndex < batches.size() - 1) {
                    try {
                        Thread.sleep(200); // 200мс пауза между батчами
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } finally {
            executor.shutdown();
        }
        long endTime = System.currentTimeMillis();
        log.debug("✅ Собрали свечи для {} монет с таймфреймом {} в {} потоков за {}с", candlesMap.size(), settings.getTimeframe(), threadCount, String.format("%.2f", (endTime - startTime) / 1000.0));
        return candlesMap;
    }

    public List<String> getValidTickersV1(List<String> swapTickers, String timeFrame, double limit, double minVolume, boolean isSorted) {
        long startTime = System.currentTimeMillis();
        AtomicInteger count = new AtomicInteger();
        // Используем 5 потоков для соблюдения лимитов OKX API
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        log.debug("🔍 Запускаем валидацию тикеров в {} потоков для {} тикеров (батчами по {})", threadCount, swapTickers.size(), BATCH_SIZE);
        List<String> result = Collections.synchronizedList(new ArrayList<>());
        int volumeAverageCount = 2; // можно сделать настраиваемым
        int candleLimit = (int) limit;
        try {
            // Обрабатываем тикеры батчами
            final List<String> validationTickersFinal = swapTickers;
            List<List<String>> batches = IntStream.range(0, (validationTickersFinal.size() + BATCH_SIZE - 1) / BATCH_SIZE)
                    .mapToObj(i -> validationTickersFinal.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, validationTickersFinal.size())))
                    .toList();

            log.debug("🔄 Обрабатываем {} батчей по {} символов для валидации", batches.size(), BATCH_SIZE);

            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                List<String> batch = batches.get(batchIndex);
                log.debug("🔄 Валидируем батч {}/{} ({} символов)", batchIndex + 1, batches.size(), batch.size());

                List<CompletableFuture<Void>> batchFutures = batch.stream()
                        .map(symbol -> CompletableFuture.runAsync(() -> {
                            try {
                                List<Candle> candles = getCandleList(symbol, timeFrame, candleLimit);
                                if (candles.size() < volumeAverageCount) {
                                    log.warn("⚠️ Недостаточно свечей для {}", symbol);
                                    count.getAndIncrement();
                                    return;
                                }

                                // берём последние N свечей и считаем средний объем
                                List<Candle> lastCandles = candles.subList(candles.size() - volumeAverageCount, candles.size());
                                double averageVolume = lastCandles.stream()
                                        .mapToDouble(Candle::getVolume)
                                        .average()
                                        .orElse(0.0);

                                if (averageVolume >= minVolume) {
                                    synchronized (result) {
                                        result.add(symbol);
                                    }
                                } else {
                                    count.getAndIncrement();
                                }
                            } catch (Exception e) {
                                log.error("❌ Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                            }
                        }, executor))
                        .toList();

                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

                // Небольшая пауза между батчами
                if (batchIndex < batches.size() - 1) {
                    try {
                        Thread.sleep(200); // 200мс пауза между батчами
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } finally {
            executor.shutdown();
        }

        long endTime = System.currentTimeMillis();
        log.debug("Всего откинули {} тикера с низким volume", count.intValue());
        log.debug("✅ Всего отобрано {} тикеров в {} потоков за {}с", result.size(), threadCount, String.format("%.2f", (endTime - startTime) / 1000.0));

        return isSorted ? result.stream().sorted().toList() : result;
    }

    public List<String> getValidTickersV2(List<String> swapTickers, String timeFrame, double limit, double minQuoteVolume, boolean isSorted) {
        long startTime = System.currentTimeMillis();
        AtomicInteger skippedCount = new AtomicInteger();
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<String> validTickers = Collections.synchronizedList(new ArrayList<>());
        int volumeAverageCount = 2; // кол-во последних свечей для усреднения
        int candleLimit = (int) limit;

        // Разбиваем тикеры на батчи
        List<List<String>> batches = IntStream.range(0, (swapTickers.size() + BATCH_SIZE - 1) / BATCH_SIZE)
                .mapToObj(i -> swapTickers.subList(i * BATCH_SIZE, Math.min((i + 1) * BATCH_SIZE, swapTickers.size())))
                .toList();

        log.debug("🔍 Валидируем {} тикеров в {} потоков (батчей: {})", swapTickers.size(), threadCount, batches.size());

        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<String> batch = batches.get(batchIndex);
            log.debug("🔄 Валидируем батч {}/{} ({} тикеров)", batchIndex + 1, batches.size(), batch.size());

            List<CompletableFuture<Void>> futures = batch.stream()
                    .map(symbol -> CompletableFuture.runAsync(() -> {
                        try {
                            List<Candle> candles = getCandleList(symbol, timeFrame, candleLimit);
                            if (candles.size() < volumeAverageCount) {
                                log.warn("⚠️ Недостаточно свечей для {}", symbol);
                                skippedCount.getAndIncrement();
                                return;
                            }

                            // Берём последние N свечей
                            List<Candle> lastCandles = candles.subList(candles.size() - volumeAverageCount, candles.size());

                            // Средний объём в quote валюте (volume * close)
                            double averageQuoteVolume = lastCandles.stream()
                                    .mapToDouble(c -> c.getVolume() * c.getClose()) //объём в quote валюте (например USDT)
                                    .average()
                                    .orElse(0.0);

                            if (averageQuoteVolume >= minQuoteVolume) {
                                validTickers.add(symbol);
                            } else {
                                skippedCount.getAndIncrement();
                            }
                        } catch (Exception e) {
                            log.error("❌ Ошибка при обработке {}: {}", symbol, e.getMessage(), e);
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Пауза между батчами для rate-limit
            if (batchIndex < batches.size() - 1) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        executor.shutdown();

        long endTime = System.currentTimeMillis();
        log.debug("Всего откинули {} тикеров с низким объёмом", skippedCount.get());
        log.debug("✅ Отобрано {} тикеров за {}с", validTickers.size(), String.format("%.2f", (endTime - startTime) / 1000.0));

        return isSorted ? validTickers.stream().sorted().toList() : validTickers;
    }


    /**
     * Получает текущую цену (last price) для указанного символа
     *
     * @param symbol Торговый символ (например, BTC-USDT)
     * @return Текущая цена или null если не удалось получить
     */
    public BigDecimal getCurrentPrice(String symbol) {
        log.info("==> getCurrentPrice: НАЧАЛО для символа {}", symbol);
        try {
            applyRateLimit();
            log.info("Rate limit применен.");

            JsonArray tickerData = getTicker(symbol);
            log.info("Получены данные тикера: {}", tickerData);

            if (tickerData != null && tickerData.size() > 0) {
                JsonObject ticker = tickerData.get(0).getAsJsonObject();
                String lastPriceStr = ticker.get("last").getAsString();
                BigDecimal lastPrice = new BigDecimal(lastPriceStr);
                log.info("Извлечена последняя цена: {}", lastPrice);
                log.info("<== getCurrentPrice: КОНЕЦ (Успех) для символа {}. Цена: {}", symbol, lastPrice);
                return lastPrice;
            }

            log.warn("⚠️ Не удалось получить цену для {}: пустой или неверный ответ от API. tickerData: {}", symbol, tickerData);
            log.info("<== getCurrentPrice: КОНЕЦ (Пустой ответ) для символа {}", symbol);
            return null;

        } catch (Exception e) {
            log.error("❌ КРИТИЧЕСКАЯ ОШИБКА при получении цены для {}: {}", symbol, e.getMessage(), e);
            log.info("<== getCurrentPrice: КОНЕЦ (Ошибка) для символа {}", symbol);
            return null;
        }
    }

}