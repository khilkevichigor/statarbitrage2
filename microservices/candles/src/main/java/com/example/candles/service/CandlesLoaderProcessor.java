package com.example.candles.service;

import com.example.candles.client.OkxFeignClient;
import com.example.candles.utils.CandleCalculatorUtil;
import com.example.shared.dto.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис-процессор для загрузки свечей с OKX и сохранения в БД
 * <p>
 * Принимает параметры:
 * - биржа (exchange)
 * - тикер (ticker)
 * - дата ДО (untilDate) - обрезанная до начала дня
 * - таймфрейм (timeframe) в формате 1H, 1D, 1m...
 * - период (period) в виде "1year", "6months"...
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandlesLoaderProcessor {

    private final OkxFeignClient okxFeignClient;
    private final CandleTransactionService candleTransactionService;

    /**
     * Главный публичный метод для загрузки свечей с OKX и сохранения в БД
     */
    public int loadAndSaveCandles(String exchange, String ticker, String untilDate, String timeframe, String period) {
        log.info("🚀 ЗАГРУЗКА СВЕЧЕЙ: Начинаем загрузку для тикера {} на бирже {}", ticker, exchange);
        log.info("📊 ПАРАМЕТРЫ: untilDate={}, timeframe={}, period={}", untilDate, timeframe, period);

        try {
            // Шаг 1: Вычисляем количество свечей для загрузки
            int candlesCount = calculateCandlesCount(timeframe, period);
            log.info("📈 РАСЧЕТ: Необходимо загрузить {} свечей для периода {} с таймфреймом {}",
                    candlesCount, period, timeframe);

            // Шаг 2: Загружаем свечи с OKX
            List<Candle> candles = loadCandlesFromOkxWithPagination(ticker, timeframe, candlesCount);
            if (candles == null || candles.isEmpty()) {
                log.error("❌ ОШИБКА: Не удалось загрузить свечи для тикера {}", ticker);
                return 0;
            }

            // Шаг 3: Логируем фактический временной диапазон загруженных свечей
            if (!candles.isEmpty()) {
                long actualOldest = candles.get(0).getTimestamp();
                long actualNewest = candles.get(candles.size() - 1).getTimestamp();
                // Определяем правильный порядок
                long oldestTimestamp = Math.min(actualOldest, actualNewest);
                long newestTimestamp = Math.max(actualOldest, actualNewest);
                
                log.info("📅 ФАКТИЧЕСКИЙ ДИАПАЗОН ЗАГРУЖЕННЫХ СВЕЧЕЙ: {} - {}", 
                        formatTimestamp(oldestTimestamp), formatTimestamp(newestTimestamp));
                
                // Рассчитаем сколько дней покрывают загруженные данные
                long daysCovered = (newestTimestamp - oldestTimestamp) / (24 * 60 * 60 * 1000L);
                log.info("⏰ ПОКРЫТИЕ: Загруженные {} свечей покрывают {} дней", candles.size(), daysCovered);
            }

            // Шаг 4: Валидируем загруженные данные
            boolean isValid = validateLoadedCandles(candles, untilDate, timeframe, period, candlesCount);
            if (!isValid) {
                log.error("❌ ВАЛИДАЦИЯ: Загруженные свечи не прошли валидацию для тикера {}", ticker);
                return 0;
            }

            // Шаг 5: Сохраняем свечи в БД
            int savedCount = saveCandlesToDatabase(ticker, timeframe, exchange, candles);

            log.info("✅ ЗАГРУЗКА ЗАВЕРШЕНА: Сохранено {} свечей для тикера {} в БД", savedCount, ticker);
            return savedCount;

        } catch (Exception e) {
            log.error("💥 ОШИБКА ЗАГРУЗКИ: Ошибка при загрузке свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Вычисляет количество свечей исходя из таймфрейма и периода
     */
    private int calculateCandlesCount(String timeframe, String period) {
        return CandleCalculatorUtil.calculateCandlesCount(timeframe, period);
    }


    /**
     * Загружает свечи с OKX с пагинацией (по 300 свечей за запрос)
     * Использует beforeTimestamp для корректной пагинации
     */
    private List<Candle> loadCandlesFromOkx(String ticker, String timeframe, int candlesCount) {
        log.info("📡 OKX ЗАПРОС: Загружаем {} свечей для тикера {} с таймфреймом {} (с пагинацией)",
                candlesCount, ticker, timeframe);

        final int MAX_CANDLES_PER_REQUEST = 300;
        List<Candle> allCandles = new ArrayList<>();
        Long beforeTimestamp = null; // Для пагинации

        try {
            // Если нужно меньше 300 свечей - делаем один запрос
            if (candlesCount <= MAX_CANDLES_PER_REQUEST) {
                List<Candle> candles = okxFeignClient.getCandles(ticker, timeframe, candlesCount);
                if (candles != null) {
                    allCandles.addAll(candles);
                    log.info("✅ OKX ОТВЕТ: Получено {} свечей за 1 запрос", candles.size());
                }
                return allCandles;
            }

            // Для больших объемов - пагинация
            int remainingCandles = candlesCount;
            int requestNumber = 1;
            int emptyRequestsCount = 0; // Счетчик запросов без новых данных

            while (remainingCandles > 0 && allCandles.size() < candlesCount) {
                int requestSize = Math.min(MAX_CANDLES_PER_REQUEST, remainingCandles);

                log.info("📡 OKX ЗАПРОС #{}: Загружаем {} свечей (осталось {})",
                        requestNumber, requestSize, remainingCandles);

                List<Candle> batchCandles = okxFeignClient.getCandles(ticker, timeframe, requestSize);

                if (batchCandles == null || batchCandles.isEmpty()) {
                    log.warn("⚠️ OKX ЗАПРОС #{}: Получен пустой ответ, завершаем загрузку", requestNumber);
                    break;
                }

                // Проверяем на дубликаты с уже загруженными свечами
                List<Candle> newCandles = filterDuplicates(allCandles, batchCandles);
                allCandles.addAll(newCandles);

                log.info("✅ OKX ЗАПРОС #{}: Получено {} свечей (добавлено {} новых, всего {})",
                        requestNumber, batchCandles.size(), newCandles.size(), allCandles.size());

                // Если получили меньше свечей чем запрашивали - значит данных больше нет
                if (batchCandles.size() < requestSize) {
                    log.info("ℹ️ OKX ЛИМИТ: Получено {} свечей из {}, исторические данные закончились",
                            batchCandles.size(), requestSize);
                    break;
                }
                
                // КРИТИЧНО: Если новых свечей нет - API возвращает дубликаты
                if (newCandles.isEmpty()) {
                    emptyRequestsCount++;
                    log.warn("⚠️ OKX ДУБЛИКАТЫ: API возвращает те же данные (попытка {}/3)", emptyRequestsCount);
                    
                    // После 3 запросов без новых данных - завершаем
                    if (emptyRequestsCount >= 3) {
                        log.warn("⚠️ OKX ЗАВЕРШЕНИЕ: 3 запроса подряд без новых данных, завершаем загрузку");
                        break;
                    }
                } else {
                    emptyRequestsCount = 0; // Сброс счетчика если получили новые данные
                }

                remainingCandles -= newCandles.size();
                requestNumber++;

                // Защита от бесконечного цикла
                if (requestNumber > 200) {
                    log.warn("⚠️ ЗАЩИТА: Достигнут лимит в 200 запросов, завершаем загрузку");
                    break;
                }

                // Небольшая пауза между запросами чтобы не перегружать API
                Thread.sleep(100);
            }

            log.info("✅ OKX ИТОГ: Загружено {} свечей за {} запросов для тикера {}",
                    allCandles.size(), requestNumber - 1, ticker);

            return allCandles;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ OKX ПРЕРВАНО: Загрузка прервана для тикера {}", ticker);
            return allCandles; // Возвращаем что успели загрузить
        } catch (Exception e) {
            log.error("❌ OKX ОШИБКА: Ошибка при загрузке свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return allCandles; // Возвращаем что успели загрузить
        }
    }

    /**
     * Фильтрует дубликаты свечей по timestamp
     */
    private List<Candle> filterDuplicates(List<Candle> existingCandles, List<Candle> newCandles) {
        if (existingCandles.isEmpty()) {
            return new ArrayList<>(newCandles);
        }

        Set<Long> existingTimestamps = existingCandles.stream()
                .map(Candle::getTimestamp)
                .collect(Collectors.toCollection(HashSet::new));

        return newCandles.stream()
                .filter(candle -> !existingTimestamps.contains(candle.getTimestamp()))
                .collect(Collectors.toList());
    }

    /**
     * Валидирует загруженные свечи
     */
    private boolean validateLoadedCandles(List<Candle> candles, String untilDate, String timeframe, String period, int expectedCount) {
        log.info("🔍 ВАЛИДАЦИЯ: Проверяем {} свечей", candles.size());

        // Проверка 1: Количество свечей
        if (candles.size() != expectedCount) {
            log.warn("⚠️ ВАЛИДАЦИЯ КОЛИЧЕСТВА: Ожидалось {} свечей, получено {}. Может быть недостаточно исторических данных.",
                    expectedCount, candles.size());
            // Не блокируем, может быть недостаточно данных на бирже
        }

        // Проверка 2: Временной диапазон
        if (candles.size() >= 2) {
            // Определяем новейшую и старейшую свечи по timestamp
            long firstTimestamp = candles.get(0).getTimestamp();
            long lastTimestamp = candles.get(candles.size() - 1).getTimestamp();

            // Правильно определяем, какая свеча новее по значению timestamp
            long newestTime = Math.max(firstTimestamp, lastTimestamp);
            long oldestTime = Math.min(firstTimestamp, lastTimestamp);

            log.info("📅 ВРЕМЕННОЙ ДИАПАЗОН: {} - {}",
                    formatTimestamp(oldestTime), formatTimestamp(newestTime));

            // Проверяем, что новейшая свеча примерно соответствует untilDate
            try {
                long untilTimestamp = Instant.parse(untilDate).toEpochMilli();
                long timeDiff = Math.abs(newestTime - untilTimestamp);
                long maxAllowedDiff = getMaxAllowedTimeDifference(timeframe);

                if (timeDiff > maxAllowedDiff) {
                    log.warn("⚠️ ВАЛИДАЦИЯ ВРЕМЕНИ: Новейшая свеча {} не соответствует ожидаемой дате {} (разница {} мс)",
                            formatTimestamp(newestTime), untilDate, timeDiff);
                }

            } catch (Exception e) {
                log.warn("⚠️ ВАЛИДАЦИЯ ВРЕМЕНИ: Ошибка парсинга даты {}: {}", untilDate, e.getMessage());
            }
        }

        log.info("✅ ВАЛИДАЦИЯ ЗАВЕРШЕНА: Свечи прошли базовую валидацию");
        return true;
    }

    /**
     * Возвращает максимально допустимую разность во времени для таймфрейма
     */
    private long getMaxAllowedTimeDifference(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "1m" -> 60 * 1000L;           // 1 минута
            case "5m" -> 5 * 60 * 1000L;       // 5 минут
            case "15m" -> 15 * 60 * 1000L;     // 15 минут
            case "1h" -> 60 * 60 * 1000L;      // 1 час
            case "4h" -> 4 * 60 * 60 * 1000L;  // 4 часа
            case "1d" -> 24 * 60 * 60 * 1000L; // 1 день
            default -> 60 * 60 * 1000L;        // По умолчанию 1 час
        };
    }

    /**
     * Сохраняет свечи в БД через CandleTransactionService
     */
    private int saveCandlesToDatabase(String ticker, String timeframe, String exchange, List<Candle> candles) {
        log.info("💾 СОХРАНЕНИЕ: Сохраняем {} свечей для тикера {} в БД", candles.size(), ticker);

        try {
            int savedCount = candleTransactionService.saveCandlesToCache(ticker, timeframe, exchange, candles);
            log.info("✅ БД СОХРАНЕНИЕ: Сохранено {} свечей для тикера {}", savedCount, ticker);
            return savedCount;

        } catch (Exception e) {
            log.error("❌ БД ОШИБКА: Ошибка сохранения свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return 0;
        }
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
     * Загружает свечи с OKX с корректной пагинацией через beforeTimestamp
     */
    private List<Candle> loadCandlesFromOkxWithPagination(String ticker, String timeframe, int candlesCount) {
        log.info("📡 OKX ЗАПРОС: Загружаем {} свечей для тикера {} с таймфреймом {} (с пагинацией beforeTimestamp)",
                candlesCount, ticker, timeframe);

        final int MAX_CANDLES_PER_REQUEST = 300;
        List<Candle> allCandles = new ArrayList<>();
        Long beforeTimestamp = null; // Для пагинации

        try {
            // Если нужно меньше 300 свечей - делаем один запрос
            if (candlesCount <= MAX_CANDLES_PER_REQUEST) {
                List<Candle> candles = okxFeignClient.getCandles(ticker, timeframe, candlesCount);
                if (candles != null) {
                    allCandles.addAll(candles);
                    log.info("✅ OKX ОТВЕТ: Получено {} свечей за 1 запрос", candles.size());
                }
                return allCandles;
            }

            // Для больших объемов - пагинация с beforeTimestamp
            int loadedCount = 0;
            int requestNumber = 1;

            while (loadedCount < candlesCount) {
                int remainingCandles = candlesCount - loadedCount;
                int requestSize = Math.min(MAX_CANDLES_PER_REQUEST, remainingCandles);

                log.info("📡 OKX ЗАПРОС #{}: Загружаем {} свечей (загружено {}/{})",
                        requestNumber, requestSize, loadedCount, candlesCount);

                List<Candle> batchCandles;
                if (beforeTimestamp == null) {
                    // Первый запрос - получаем последние свечи
                    batchCandles = okxFeignClient.getCandles(ticker, timeframe, requestSize);
                } else {
                    // Последующие запросы - используем пагинацию
                    batchCandles = okxFeignClient.getCandlesBefore(ticker, timeframe, requestSize, beforeTimestamp);
                }

                if (batchCandles == null || batchCandles.isEmpty()) {
                    log.info("ℹ️ OKX ЗАВЕРШЕНИЕ: Больше данных нет, завершаем загрузку на {} запросе", requestNumber);
                    break;
                }

                allCandles.addAll(batchCandles);
                loadedCount += batchCandles.size();

                log.info("✅ OKX ЗАПРОС #{}: Получено {} свечей (всего загружено {}/{})",
                        requestNumber, batchCandles.size(), loadedCount, candlesCount);

                // Если получили меньше свечей чем запрашивали - значит данных больше нет
                if (batchCandles.size() < requestSize) {
                    log.info("ℹ️ OKX ЛИМИТ: Получено {} свечей из {}, исторические данные закончились",
                            batchCandles.size(), requestSize);
                    break;
                }

                // Устанавливаем timestamp для следующего запроса (самая старая свеча из текущего батча)
                beforeTimestamp = batchCandles.get(batchCandles.size() - 1).getTimestamp();
                requestNumber++;

                // Защита от бесконечного цикла
                if (requestNumber > 200) {
                    log.warn("⚠️ ЗАЩИТА: Достигнут лимит в 200 запросов, завершаем загрузку");
                    break;
                }

                // Пауза между запросами для соблюдения rate limit
                Thread.sleep(120);
            }

            log.info("✅ OKX ИТОГ: Загружено {} свечей за {} запросов для тикера {}",
                    allCandles.size(), requestNumber - 1, ticker);

            return allCandles;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ OKX ПРЕРВАНО: Загрузка прервана для тикера {}", ticker);
            return allCandles; // Возвращаем что успели загрузить
        } catch (Exception e) {
            log.error("❌ OKX ОШИБКА: Ошибка при загрузке свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return allCandles; // Возвращаем что успели загрузить
        }
    }
}