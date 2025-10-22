package com.example.candles.service;

import com.example.candles.repositories.CachedCandleRepository;
import com.example.candles.utils.CandleCalculatorUtil;
import com.example.shared.dto.Candle;
import com.example.shared.models.CachedCandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Сервис-процессор для получения свечей из кэша с расширенной валидацией
 * <p>
 * Принимает параметры:
 * - биржа (exchange)
 * - тикер (ticker)
 * - дата ДО (untilDate) - обрезанная до начала дня в формате 2025-09-27T00:00:00Z
 * - таймфрейм (timeframe) в формате 1H, 1D, 1m...
 * - период (period) в виде "1year", "6months"...
 * <p>
 * Выполняет двухэтапную валидацию:
 * 1. Валидация по количеству свечей (с учетом допустимых отклонений)
 * 2. Валидация консистентности таймштампов (проверка временных интервалов между свечами)
 * <p>
 * При обнаружении проблем автоматически догружает недостающие данные:
 * - Для проблем с количеством: стандартная догрузка всего диапазона
 * - Для пропусков в таймштампах: специальная догрузка с повторными попытками до восстановления консистентности
 * <p>
 * Гарантирует 100% консистентность и непрерывность временных рядов свечей.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheValidatedCandlesProcessor {

    private final CachedCandleRepository cachedCandleRepository;
    private final CandlesLoaderProcessor candlesLoaderProcessor;
    
    // Синхронизация догрузки по тикерам для избежания дублирования запросов в многопоточной среде
    private final ConcurrentHashMap<String, Object> tickerLocks = new ConcurrentHashMap<>();

    /**
     * Главный публичный метод для получения валидированных свечей из кэша
     * 
     * ПСЕВДОКОД ЛОГИКИ:
     * {
     *   БЛОК 1: ПОДГОТОВКА И РАСЧЕТЫ
     *   {
     *     1.1. Вычисляем ожидаемое количество свечей для данного периода до untilDate
     *     1.2. Парсим untilDate в миллисекунды для работы с БД
     *   }
     *   
     *   БЛОК 2: ПОЛУЧЕНИЕ ДАННЫХ ИЗ КЭША
     *   {
     *     2.1. Запрашиваем из БД последние N свечей ДО untilDate (ascending order)
     *     2.2. Ограничиваем количество до expectedCount
     *   }
     *   
     *   БЛОК 3: ДВУХЭТАПНАЯ ВАЛИДАЦИЯ
     *   {
     *     3.1. Валидация по количеству: сравниваем полученное vs ожидаемое количество
     *     3.2. Валидация консистентности: проверяем отсутствие пропусков во временных интервалах
     *   }
     *   
     *   БЛОК 4: СИСТЕМА RETRY С DOGРУЗКОЙ (максимум 2 попытки)
     *   {
     *     ПОКА (валидация провалена И попытки остались) {
     *       4.1. Логируем причину провала валидации
     *       4.2. ЕСЛИ последняя попытка → возвращаем пустой список (тикер неактивный)
     *       4.3. СИНХРОНИЗАЦИЯ: блокируем догрузку по ключу ticker:timeframe:exchange
     *       4.4. ВЫБОР ТИПА ДОГРУЗКИ:
     *            ЕСЛИ есть пропуски в timestamps → специальная догрузка для gaps
     *            ИНАЧЕ → обычная полная догрузка с OKX API
     *       4.5. Повторно запрашиваем данные из кэша после догрузки
     *       4.6. Повторно валидируем количество и консистентность
     *       4.7. ЕСЛИ догрузка провалена → возвращаем пустой список
     *     }
     *   }
     *   
     *   БЛОК 5: ВОЗВРАТ РЕЗУЛЬТАТА
     *   {
     *     5.1. Возвращаем валидированный список свечей
     *     5.2. При любой ошибке → возвращаем пустой список
     *   }
     * }
     */
    public List<Candle> getValidatedCandlesFromCache(String exchange, String ticker, String untilDate, String timeframe, String period) {
        log.debug("🔍 КЭШ ЗАПРОС: Получаем свечи для тикера {} на бирже {}", ticker, exchange);
        log.debug("📊 ПАРАМЕТРЫ: untilDate={}, timeframe={}, period={}", untilDate, timeframe, period);

        // Таймфрейм передается напрямую в методы для избежания race condition

        try {
            /*
             * БЛОК 1: ПОДГОТОВКА И РАСЧЕТЫ
             * - Вычисляем сколько свечей должно быть для заданного периода до untilDate
             * - Конвертируем untilDate в миллисекунды для сравнения с timestamp в БД
             */
            // Шаг 1.1: Вычисляем ожидаемое количество свечей С УЧЕТОМ untilDate
            int expectedCandlesCount = CandleCalculatorUtil.calculateCandlesCountUntilDate(ticker, timeframe, period, untilDate);
            log.debug("🎯 ОЖИДАНИЯ: {} свечей для периода '{}' с таймфреймом {} до {}", expectedCandlesCount, period, timeframe, untilDate);

            // Шаг 1.2: Парсим untilDate в миллисекунды
            long untilTimestamp = parseUntilDate(untilDate);
            
            /*
             * БЛОК 2: ПОЛУЧЕНИЕ ДАННЫХ ИЗ КЭША
             * - Запрашиваем из PostgreSQL последние N свечей ДО указанной даты
             * - Сортируем по возрастанию времени для корректного анализа
             */
            // Шаг 2.1: Получаем свечи ДО untilDate из кэша (PostgreSQL)
            List<Candle> cachedCandles = getCandlesFromCacheByActualRange(exchange, ticker, timeframe, expectedCandlesCount, untilTimestamp);

            /*
             * БЛОК 3: ДВУХЭТАПНАЯ ВАЛИДАЦИЯ
             * Этап 1: Проверка количества (достаточно ли свечей)
             * Этап 2: Проверка консистентности временных интервалов (нет ли пропусков)
             */
            // Шаг 3.1: Валидируем полученные свечи по количеству
            ValidationResult validationResult = validateCandlesByCount(cachedCandles, expectedCandlesCount, ticker, timeframe);

            // Шаг 3.2: Валидируем консистентность таймштампов свечей (проверяем пропуски)
            TimestampValidationResult timestampValidation = validateCandlesConsistency(cachedCandles, timeframe, ticker);

            /*
             * БЛОК 4: СИСТЕМА RETRY С ДОГРУЗКОЙ (максимум 2 попытки)
             * Алгоритм:
             * 1. ЕСЛИ валидация прошла → выходим из цикла
             * 2. ЕСЛИ валидация провалена → пытаемся догрузить недостающие данные
             * 3. Максимум 2 попытки, после чего считаем тикер неактивным
             * 4. Синхронизация по тикеру для избежания параллельных догрузок
             */
            // Шаг 4: Система retry с догрузкой (максимум 2 попытки)
            final int MAX_VALIDATION_ATTEMPTS = 2;
            for (int attempt = 1; attempt <= MAX_VALIDATION_ATTEMPTS; attempt++) {
                
                if (!validationResult.isValid || !timestampValidation.isValid) {
                    // Логируем причины провала валидации
                    if (!validationResult.isValid) {
                        log.debug("⚠️ ВАЛИДАЦИЯ КОЛИЧЕСТВА ПРОВАЛЕНА (попытка {}/{}): {} {}", attempt, MAX_VALIDATION_ATTEMPTS, ticker, validationResult.reason);
                    }
                    if (!timestampValidation.isValid) {
                        log.debug("⚠️ ВАЛИДАЦИЯ КОНСИСТЕНТНОСТИ ПРОВАЛЕНА (попытка {}/{}): {} {}", attempt, MAX_VALIDATION_ATTEMPTS, ticker, timestampValidation.reason);
                    }
                    
                    // Если это последняя попытка - не делаем догрузку, возвращаем пустой список
                    if (attempt == MAX_VALIDATION_ATTEMPTS) {
                        log.debug("⚠️ ИСЧЕРПАНЫ ПОПЫТКИ: {} Максимум {} попыток валидации - возможно неактивный тикер", ticker, MAX_VALIDATION_ATTEMPTS);
                        return List.of();
                    }
                    
                    log.debug("🔄 ДОГРУЗКА (попытка {}/{}): Запускаем загрузку свежих данных для тикера {}", attempt, MAX_VALIDATION_ATTEMPTS, ticker);

                    /*
                     * СИНХРОНИЗАЦИЯ ПО ТИКЕРУ
                     * - Создаем уникальный ключ блокировки для каждого ticker:timeframe:exchange
                     * - Предотвращаем одновременную догрузку одного и того же тикера в разных потоках
                     * - Избегаем дублирующих API запросов к OKX
                     */
                    // Синхронизируем догрузку по тикеру для избежания дублирующих запросов к OKX API
                    String lockKey = ticker + ":" + timeframe + ":" + exchange;
                    Object lock = tickerLocks.computeIfAbsent(lockKey, k -> new Object());
                    
                    int loadedCount;
                    synchronized (lock) {
                        log.debug("🔒 СИНХРОНИЗАЦИЯ: Захватили блокировку для догрузки тикера {}", ticker);
                        
                        /*
                         * ВЫБОР ТИПА ДОГРУЗКИ В ЗАВИСИМОСТИ ОТ ПРОБЛЕМЫ:
                         * Случай 1: Есть пропуски в таймштампах → специальная догрузка для заполнения gaps
                         * Случай 2: Недостаточно свечей → полная догрузка всего диапазона
                         */
                        // Если есть пропуски в таймштампах - используем специальную догрузку
                        if (!timestampValidation.isValid && !timestampValidation.gaps.isEmpty()) {
                            loadedCount = loadMissingCandlesForGaps(timestampValidation.gaps, exchange, ticker, timeframe, period, untilDate);
                        } else {
                            // Обычная догрузка для проблем с количеством
                            loadedCount = candlesLoaderProcessor.loadAndSaveCandles(exchange, ticker, untilDate, timeframe, period);
                        }
                        
                        log.debug("🔓 СИНХРОНИЗАЦИЯ: Освободили блокировку для тикера {} (загружено {} свечей)", ticker, loadedCount);
                    }
                    
                    /*
                     * ОБРАБОТКА РЕЗУЛЬТАТА ДОГРУЗКИ:
                     * Успех: повторно валидируем данные из кэша
                     * Провал: возвращаем пустой список (тикер неактивный)
                     */
                    if (loadedCount > 0) {
                        log.debug("✅ ДОГРУЗКА ЗАВЕРШЕНА (попытка {}/{}): Загружено {} свечей, повторно получаем из кэша", attempt, MAX_VALIDATION_ATTEMPTS, loadedCount);

                        // Повторно получаем из кэша после догрузки ДО untilDate
                        cachedCandles = getCandlesFromCacheByActualRange(exchange, ticker, timeframe, expectedCandlesCount, untilTimestamp);
                        validationResult = validateCandlesByCount(cachedCandles, expectedCandlesCount, ticker, timeframe);
                        timestampValidation = validateCandlesConsistency(cachedCandles, timeframe, ticker);

                        // Проверяем результат повторной валидации - если все хорошо, цикл прервется на следующей итерации
                        // Если плохо - попробуем еще раз (если есть попытки)
                    } else {
                        log.error("❌ ДОГРУЗКА ПРОВАЛЕНА (попытка {}/{}): Не удалось загрузить данные для тикера {} - возможно неактивный тикер", attempt, MAX_VALIDATION_ATTEMPTS, ticker);
                        return List.of(); // Возвращаем пустой список сразу
                    }
                } else {
                    /*
                     * УСПЕШНАЯ ВАЛИДАЦИЯ:
                     * - Количество свечей соответствует ожидаемому
                     * - Временные интервалы консистентны
                     * - Выходим из цикла retry
                     */
                    // Валидация прошла успешно - выходим из цикла
                    break;
                }
            }

            /*
             * БЛОК 5: ВОЗВРАТ РЕЗУЛЬТАТА
             * - Возвращаем список валидированных свечей
             * - Все свечи прошли проверку количества и консистентности
             */
            // Шаг 5: Возвращаем валидированные свечи
            log.debug("✅ КЭШ РЕЗУЛЬТАТ: Возвращаем {} валидированных свечей для тикера {}", cachedCandles.size(), ticker);
            return cachedCandles;

        } catch (Exception e) {
            /*
             * ОБРАБОТКА КРИТИЧЕСКИХ ОШИБОК:
             * - Ошибки парсинга, БД, сети
             * - Возвращаем пустой список для безопасности
             */
            log.error("💥 КЭШ ОШИБКА: Ошибка при получении свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return List.of();
        }
    }

//    /**
//     * Вычисляет ожидаемые параметры для валидации
//     */
//    private ExpectedParameters calculateExpectedParameters(String ticker, String untilDate, String timeframe, String period) {
//        log.info("📐 РАСЧЕТ ПАРАМЕТРОВ: untilDate={}, timeframe={}, period={}", untilDate, timeframe, period);
//
//        // Парсим дату "до"
//        long untilTimestamp = parseUntilDate(untilDate);
//
//        // Вычисляем количество свечей
//        int candlesCount = calculateCandlesCount(ticker, timeframe, period);
//
//        // Вычисляем ожидаемое время старейшей свечи
//        long timeframeDurationMs = getTimeframeDurationInMillis(timeframe);
//        // Новейшая свеча должна быть НА ОДИН ДЕНЬ РАНЬШЕ untilDate (исключаем граничную точку)
//        long expectedNewestTime = untilTimestamp - (24 * 60 * 60 * 1000L); // -1 день
//        // Старейшая свеча рассчитывается от новейшей
//        long expectedOldestTime = expectedNewestTime - ((candlesCount - 1) * timeframeDurationMs);
//
//        return new ExpectedParameters(candlesCount, expectedOldestTime, expectedNewestTime);
//    }

    /**
     * Парсит дату в формате 2025-09-27T00:00:00Z в миллисекунды
     */
    private long parseUntilDate(String untilDate) {
        try {
            return Instant.parse(untilDate).toEpochMilli();
        } catch (Exception e) {
            log.error("❌ ОШИБКА ПАРСИНГА ДАТЫ: Не удалось распарсить дату {}: {}", untilDate, e.getMessage());
            // Возвращаем текущее время как fallback
            return System.currentTimeMillis();
        }
    }

//    /**
//     * Вычисляет количество свечей исходя из таймфрейма и периода
//     */
//    private int calculateCandlesCount(String ticker, String timeframe, String period) {
//        return CandleCalculatorUtil.calculateCandlesCount(ticker, timeframe, period);
//    }


    /**
     * Возвращает длительность таймфрейма в миллисекундах
     */
    private long getTimeframeDurationInMillis(String timeframe) {
        return switch (timeframe) {
            case "1m" -> 60 * 1000L;                    // 1 минута
            case "5m" -> 5 * 60 * 1000L;                // 5 минут
            case "15m" -> 15 * 60 * 1000L;              // 15 минут
            case "1H" -> 60 * 60 * 1000L;               // 1 час
            case "4H" -> 4 * 60 * 60 * 1000L;           // 4 часа
            case "1D" -> 24 * 60 * 60 * 1000L;          // 1 день
            case "1W" -> 7 * 24 * 60 * 60 * 1000L;      // 1 неделя
            case "1M" -> 30L * 24 * 60 * 60 * 1000L;    // 1 месяц (приблизительно)
            default -> {
                log.warn("⚠️ НЕИЗВЕСТНЫЙ ТАЙМФРЕЙМ: {}, используем 1H", timeframe);
                yield 60 * 60 * 1000L;
            }
        };
    }

    /**
     * Получает последние свечи из кэша по реальному диапазону, отталкиваясь от untilDate
     */
    private List<Candle> getCandlesFromCacheByActualRange(String exchange, String ticker, String timeframe, int expectedCount, long untilTimestamp) {
        log.debug("🗃️ КЭШ ЗАПРОС: Получаем последние {} свечей для тикера {} ДО даты {}",
                expectedCount, ticker, formatTimestamp(untilTimestamp));

        try {
            // Получаем все свечи для данного тикера ДО untilDate, отсортированные по убыванию времени
            List<CachedCandle> cachedCandles = cachedCandleRepository
                    .findByTickerAndTimeframeAndExchangeAndTimestampLessThanOrderByTimestampDesc(
                            ticker, timeframe, exchange, untilTimestamp);

            log.debug("🔍 КЭШ ПОИСК: Найдено {} свечей в БД для тикера {} ДО даты {}",
                    cachedCandles.size(), ticker, formatTimestamp(untilTimestamp));

            // Берем только нужное количество последних свечей (ДО untilDate)
            List<CachedCandle> limitedCandles = cachedCandles.stream()
                    .limit(expectedCount)
                    .collect(Collectors.toList());

            // Сортируем обратно по возрастанию времени для корректного порядка
            limitedCandles.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

            // Конвертируем в Candle
            List<Candle> candles = limitedCandles.stream()
                    .map(CachedCandle::toCandle)
                    .collect(Collectors.toList());

            log.debug("✅ КЭШ ОТВЕТ: Получено {} свечей для тикера {} из кэша ДО untilDate", candles.size(), ticker);

            if (!candles.isEmpty()) {
                long actualOldest = candles.get(0).getTimestamp();
                long actualNewest = candles.get(candles.size() - 1).getTimestamp();
                log.debug("📅 ФАКТИЧЕСКИЙ ДИАПАЗОН ИЗ КЭША: {} - {}",
                        formatTimestamp(actualOldest), formatTimestamp(actualNewest));
                log.debug("🔍 ПРОВЕРКА UNTILDATE: Новейшая свеча {} < untilDate {} = {}",
                        formatTimestamp(actualNewest), formatTimestamp(untilTimestamp), 
                        actualNewest < untilTimestamp);
            }

            return candles;

        } catch (Exception e) {
            log.error("❌ КЭШ ОШИБКА: Ошибка получения свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return List.of();
        }
    }

//    /**
//     * Получает свечи из кэша в заданном временном диапазоне (устаревший метод, используется для старых тестов)
//     */
//    private List<Candle> getCandlesFromCache(String exchange, String ticker, String timeframe, int limit, long expectedOldestTime, long expectedNewestTime) {
//        log.info("🗃️ КЭШ ЗАПРОС: Получаем {} свечей для тикера {} в диапазоне {} - {}",
//                limit, ticker, formatTimestamp(expectedOldestTime), formatTimestamp(expectedNewestTime));
//
//        try {
//            // Используем миллисекунды напрямую - timestamp в БД хранится в миллисекундах
//            // Используем точный запрос по временному диапазону вместо фильтрации
//            List<CachedCandle> cachedCandles = cachedCandleRepository
//                    .findByTickerAndTimeframeAndExchangeAndTimestampBetweenOrderByTimestampAsc(
//                            ticker, timeframe, exchange, expectedOldestTime, expectedNewestTime);
//
//            log.info("🔍 КЭШ ПОИСК: Получено {} свечей из кэша по точному диапазону {} - {}",
//                    cachedCandles.size(), formatTimestamp(expectedOldestTime), formatTimestamp(expectedNewestTime));
//
//            // Конвертируем в Candle (уже отсортированы по возрастанию timestamp)
//            List<Candle> candles = cachedCandles.stream()
//                    .map(CachedCandle::toCandle)
//                    .collect(Collectors.toList());
//
//            log.debug("✅ КЭШ ОТВЕТ: Получено {} свечей для тикера {} из кэша по точному диапазону", candles.size(), ticker);
//
//            if (!candles.isEmpty()) {
//                long actualOldest = candles.get(0).getTimestamp();
//                long actualNewest = candles.get(candles.size() - 1).getTimestamp();
//                log.debug("📅 ФАКТИЧЕСКИЙ ДИАПАЗОН ИЗ КЭША: {} - {}",
//                        formatTimestamp(actualOldest), formatTimestamp(actualNewest));
//            }
//
//            return candles;
//
//        } catch (Exception e) {
//            log.error("❌ КЭШ ОШИБКА: Ошибка получения свечей для тикера {}: {}", ticker, e.getMessage(), e);
//            return List.of();
//        }
//    }

//    /**
//     * Фильтрует свечи точно до untilDate и берет нужное количество
//     */
//    private List<Candle> filterCandlesUntilDate(List<Candle> candles, long untilTimestamp, int expectedCount, String timeframe) {
//        if (candles.isEmpty()) {
//            log.warn("⚠️ ФИЛЬТР UNTILDATE: Список свечей пуст, нечего фильтровать");
//            return candles;
//        }
//
//        log.info("🔍 ФИЛЬТР UNTILDATE: Фильтруем {} свечей до даты {}", candles.size(), formatTimestamp(untilTimestamp));
//
//        // Фильтруем свечи строго ДО untilDate (не включительно)
//        List<Candle> filteredCandles = candles.stream()
//                .filter(candle -> candle.getTimestamp() < untilTimestamp)
//                .collect(Collectors.toList());
//
//        log.info("🔍 ФИЛЬТР РЕЗУЛЬТАТ: После фильтрации по дате осталось {} свечей", filteredCandles.size());
//
//        // Если после фильтрации осталось больше чем нужно - берем последние N свечей
//        if (filteredCandles.size() > expectedCount) {
//            // Сортируем по убыванию времени, берем первые N, затем сортируем обратно по возрастанию
//            List<Candle> lastNCandles = filteredCandles.stream()
//                    .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp())) // По убыванию
//                    .limit(expectedCount)
//                    .sorted((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp())) // Обратно по возрастанию
//                    .collect(Collectors.toList());
//
//            log.info("🔍 ФИЛЬТР ОБРЕЗКА: Взяли последние {} свечей из {} доступных", expectedCount, filteredCandles.size());
//            filteredCandles = lastNCandles;
//        }
//
//        if (!filteredCandles.isEmpty()) {
//            long actualOldest = filteredCandles.get(0).getTimestamp();
//            long actualNewest = filteredCandles.get(filteredCandles.size() - 1).getTimestamp();
//            log.info("📅 ФИНАЛЬНЫЙ ДИАПАЗОН ПОСЛЕ ФИЛЬТРА: {} - {}",
//                    formatTimestamp(actualOldest), formatTimestamp(actualNewest));
//
//            // Проверяем, что последняя свеча действительно до untilDate
//            if (actualNewest >= untilTimestamp) {
//                log.warn("⚠️ ФИЛЬТР ПРОБЛЕМА: Новейшая свеча {} >= untilDate {}",
//                        formatTimestamp(actualNewest), formatTimestamp(untilTimestamp));
//            } else {
//                log.info("✅ ФИЛЬТР ПРОВЕРКА: Новейшая свеча {} < untilDate {} - корректно",
//                        formatTimestamp(actualNewest), formatTimestamp(untilTimestamp));
//            }
//        }
//
//        return filteredCandles;
//    }

    /**
     * Валидирует консистентность таймштампов свечей - проверяет отсутствие пропусков во временных интервалах
     * @param candles список свечей для проверки (должен быть отсортирован по возрастанию timestamp)
     * @param timeframe таймфрейм (1m, 5m, 15m, 1H, 4H, 1D и т.д.)
     * @param ticker название тикера для логирования
     * @return результат валидации с информацией о найденных пропусках
     */
    private TimestampValidationResult validateCandlesConsistency(List<Candle> candles, String timeframe, String ticker) {
        log.debug("🔍 ВАЛИДАЦИЯ ТАЙМШТАМПОВ: Проверка консистентности {} свечей для тикера {} с таймфреймом {}",
                candles.size(), ticker, timeframe);
        
        if (candles == null || candles.isEmpty()) {
            log.warn("⚠️ ВАЛИДАЦИЯ ТАЙМШТАМПОВ: Список свечей пуст для тикера {}", ticker);
            return new TimestampValidationResult(true, "Список свечей пуст", List.of());
        }
        
        if (candles.size() < 2) {
            log.warn("⚠️ ВАЛИДАЦИЯ ТАЙМШТАМПОВ: Слишком мало свечей ({}) для проверки консистентности тикера {}",
                    candles.size(), ticker);
            return new TimestampValidationResult(true, "Недостаточно свечей для проверки", List.of());
        }
        
        long timeframeDurationMs = getTimeframeDurationInMillis(timeframe);
        List<TimestampGap> gaps = new ArrayList<>();
        
        // Проверяем интервалы между соседними свечами
        for (int i = 1; i < candles.size(); i++) {
            long previousTimestamp = candles.get(i - 1).getTimestamp();
            long currentTimestamp = candles.get(i).getTimestamp();
            long actualInterval = currentTimestamp - previousTimestamp;
            
            // Проверяем, что интервал соответствует таймфрейму (с небольшой погрешностью)
            if (Math.abs(actualInterval - timeframeDurationMs) > timeframeDurationMs * 0.1) { // 10% погрешность
                long missedCandles = (actualInterval / timeframeDurationMs) - 1;
                if (missedCandles > 0) {
                    TimestampGap gap = new TimestampGap(
                            previousTimestamp, 
                            currentTimestamp, 
                            (int) missedCandles,
                            i - 1, // позиция предыдущей свечи
                            i      // позиция текущей свечи
                    );
                    gaps.add(gap);
                    
                    log.warn("⚠️ ПРОПУСК СВЕЧЕЙ: Между позициями {} и {} найден пропуск в {} свечей. " +
                            "Предыдущая: {}, текущая: {}, ожидаемый интервал: {} мс, фактический: {} мс",
                            i - 1, i, missedCandles,
                            formatTimestamp(previousTimestamp), formatTimestamp(currentTimestamp),
                            timeframeDurationMs, actualInterval);
                }
            }
        }
        
        if (gaps.isEmpty()) {
            log.debug("✅ ВАЛИДАЦИЯ ТАЙМШТАМПОВ: Свечи для тикера {} прошли проверку консистентности. " +
                    "Временные интервалы соответствуют таймфрейму {}", ticker, timeframe);
            return new TimestampValidationResult(true, "Консистентность таймштампов корректна", gaps);
        } else {
            int totalMissedCandles = gaps.stream().mapToInt(TimestampGap::getMissedCandlesCount).sum();
            String reason = String.format("Найдено %d пропусков с общим количеством недостающих свечей: %d", 
                    gaps.size(), totalMissedCandles);
            log.warn("⚠️ ВАЛИДАЦИЯ ТАЙМШТАМПОВ: {}", reason);
            
            // Детальное логирование каждого пропуска
            for (int i = 0; i < gaps.size(); i++) {
                TimestampGap gap = gaps.get(i);
                log.warn("⚠️ ПРОПУСК #{}: {} недостающих свечей между {} и {}", 
                        i + 1, gap.getMissedCandlesCount(),
                        formatTimestamp(gap.getStartTimestamp()), formatTimestamp(gap.getEndTimestamp()));
            }
            
            return new TimestampValidationResult(false, reason, gaps);
        }
    }

    /**
     * Догружает недостающие свечи для заполнения пропусков в таймштампах
     * @param gaps список пропусков для заполнения
     * @param exchange биржа
     * @param ticker тикер
     * @param timeframe таймфрейм
     * @param period период
     * @param untilDate дата до которой загружать
     * @return общее количество догруженных свечей
     */
    private int loadMissingCandlesForGaps(List<TimestampGap> gaps, String exchange, String ticker, 
                                         String timeframe, String period, String untilDate) {
        if (gaps.isEmpty()) {
            log.info("✅ ДОГРУЗКА ПРОПУСКОВ: Нет пропусков для догрузки тикера {}", ticker);
            return 0;
        }
        
        log.info("🔄 ДОГРУЗКА ПРОПУСКОВ: Начинаем догрузку для {} пропусков в тикере {}", gaps.size(), ticker);
        
        int totalLoadedCandles = 0;
        int maxRetries = 3; // Максимальное количество попыток догрузки
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            log.info("🔄 ПОПЫТКА #{}: Догрузка недостающих свечей для тикера {}", attempt, ticker);
            
            // Делаем общую догрузку всего диапазона - это проще и надёжнее
            // чем пытаться догружать каждый пропуск отдельно
            int loadedCount = candlesLoaderProcessor.loadAndSaveCandles(exchange, ticker, untilDate, timeframe, period);
            
            if (loadedCount > 0) {
                log.info("✅ ПОПЫТКА #{}: Загружено {} свечей для тикера {}", attempt, loadedCount, ticker);
                totalLoadedCandles += loadedCount;
                
                // Повторно проверяем консистентность после догрузки
                long untilTimestamp = parseUntilDate(untilDate);
                int expectedCandlesCount = CandleCalculatorUtil.calculateCandlesCountUntilDate(ticker, timeframe, period, untilDate);
                List<Candle> reloadedCandles = getCandlesFromCacheByActualRange(exchange, ticker, timeframe, expectedCandlesCount, untilTimestamp);
                
                TimestampValidationResult revalidationResult = validateCandlesConsistency(reloadedCandles, timeframe, ticker);
                
                if (revalidationResult.isValid) {
                    log.info("✅ ПОПЫТКА #{}: Консистентность восстановлена для тикера {} после догрузки", attempt, ticker);
                    break;
                } else {
                    log.warn("⚠️ ПОПЫТКА #{}: Остались пропуски после догрузки для тикера {}: {}", 
                            attempt, ticker, revalidationResult.reason);
                    
                    if (attempt == maxRetries) {
                        log.error("❌ ДОГРУЗКА ПРОПУСКОВ: Исчерпаны попытки восстановления консистентности для тикера {}. " +
                                "Остается {} пропусков", ticker, revalidationResult.gaps.size());
                    } else {
                        log.info("🔄 ПОВТОРНАЯ ПОПЫТКА: Будет выполнена повторная догрузка для тикера {}", ticker);
                    }
                }
            } else {
                log.warn("⚠️ ПОПЫТКА #{}: Не удалось догрузить свечи для тикера {} (получено 0 свечей)", attempt, ticker);
                
                if (attempt == maxRetries) {
                    log.error("❌ ДОГРУЗКА ПРОПУСКОВ: Не удалось догрузить свечи для тикера {} за {} попыток", ticker, maxRetries);
                }
            }
        }
        
        log.info("📊 ИТОГО ДОГРУЗКА ПРОПУСКОВ: Загружено {} свечей для тикера {} за {} попыток", 
                totalLoadedCandles, ticker, Math.min(maxRetries, totalLoadedCandles > 0 ? 1 : maxRetries));
        
        return totalLoadedCandles;
    }

    /**
     * Валидирует свечи только по количеству (упрощенная версия без проверки временного диапазона)
     * Использует увеличенную погрешность для случаев с untilDate
     */
    private ValidationResult validateCandlesByCount(List<Candle> candles, int expectedCount, String ticker, String timeframe) {
        log.debug("🔍 ВАЛИДАЦИЯ КЭШа: Проверяем {} свечей для тикера {}", candles.size(), ticker);

        // Простая валидация с допустимым отклонением
        int allowedDifference = CandleCalculatorUtil.getAllowedDifferenceWithUntilDate(timeframe, expectedCount);
        int actualDifference = Math.abs(candles.size() - expectedCount);
        
        if (actualDifference > allowedDifference) {
            String tolerance = CandleCalculatorUtil.getToleranceDescription(timeframe) + " + untilDate буфер";
            String reason = String.format("Отклонение в количестве свечей превышает допустимое: ожидалось %d, получено %d (отклонение %d > допустимое %d, %s)",
                    expectedCount, candles.size(), actualDifference, allowedDifference, tolerance);
            log.debug("⚠️ ВАЛИДАЦИЯ КОЛИЧЕСТВА: {} {}", ticker, reason);
            return new ValidationResult(false, reason);
        }

        log.debug("✅ ВАЛИДАЦИЯ УСПЕШНА: Свечи для тикера {} прошли проверку по количеству с untilDate (допустимое отклонение {})", ticker, allowedDifference);
        return new ValidationResult(true, "Валидация по количеству с untilDate успешна");
    }

//    /**
//     * Валидирует полученные из кэша свечи (полная версия с проверкой временного диапазона)
//     */
//    private ValidationResult validateCachedCandles(List<Candle> candles, ExpectedParameters expected, String ticker) {
//        log.info("🔍 ВАЛИДАЦИЯ КЭШа: Проверяем {} свечей для тикера {}", candles.size(), ticker);
//
//        // Проверка 1: Количество свечей с использованием точной валидации из утилитного класса
//        if (!CandleCalculatorUtil.isValidCandlesCount(currentTimeframe, expected.candlesCount, candles.size())) {
//            int allowedDifference = CandleCalculatorUtil.getAllowedDifference(currentTimeframe, expected.candlesCount);
//            int actualDifference = Math.abs(candles.size() - expected.candlesCount);
//            String tolerance = CandleCalculatorUtil.getToleranceDescription(currentTimeframe);
//
//            String reason = String.format("Отклонение в количестве свечей превышает допустимое: ожидалось %d, получено %d (отклонение %d > допустимое %d, %s)",
//                    expected.candlesCount, candles.size(), actualDifference, allowedDifference, tolerance);
//            log.warn("⚠️ ВАЛИДАЦИЯ КОЛИЧЕСТВА: {}", reason);
//            return new ValidationResult(false, reason);
//        }
//
//        // Проверка 2: Временной диапазон (только если есть свечи)
//        if (!candles.isEmpty()) {
//            long actualOldestTime = candles.get(0).getTimestamp();          // Первая = самая старая (сортированы по возрастанию)
//            long actualNewestTime = candles.get(candles.size() - 1).getTimestamp(); // Последняя = самая новая
//
//            log.info("📅 ФАКТИЧЕСКИЙ ДИАПАЗОН: {} - {}",
//                    formatTimestamp(actualOldestTime), formatTimestamp(actualNewestTime));
//            log.info("📅 ОЖИДАЕМЫЙ ДИАПАЗОН: {} - {}",
//                    formatTimestamp(expected.expectedOldestTime), formatTimestamp(expected.expectedNewestTime));
//
//            // Допускаем погрешность в 1% от общего периода
//            long totalPeriod = expected.expectedNewestTime - expected.expectedOldestTime;
//            long allowedDifference = Math.max(totalPeriod / 100, 60 * 60 * 1000L); // Минимум 1 час
//
//            // Проверяем старейшую свечу
//            long oldestTimeDiff = Math.abs(actualOldestTime - expected.expectedOldestTime);
//            if (oldestTimeDiff > allowedDifference) {
//                String reason = String.format("Диапазон съехал: старейшая свеча %s не соответствует ожидаемой %s (разница %d мс)",
//                        formatTimestamp(actualOldestTime), formatTimestamp(expected.expectedOldestTime), oldestTimeDiff);
//                log.warn("⚠️ ВАЛИДАЦИЯ ДИАПАЗОНА: {}", reason);
//                return new ValidationResult(false, reason);
//            }
//
//            // Проверяем новейшую свечу
//            long newestTimeDiff = Math.abs(actualNewestTime - expected.expectedNewestTime);
//            if (newestTimeDiff > allowedDifference) {
//                String reason = String.format("Диапазон съехал: новейшая свеча %s не соответствует ожидаемой %s (разница %d мс)",
//                        formatTimestamp(actualNewestTime), formatTimestamp(expected.expectedNewestTime), newestTimeDiff);
//                log.warn("⚠️ ВАЛИДАЦИЯ ДИАПАЗОНА: {}", reason);
//                return new ValidationResult(false, reason);
//            }
//        }
//
//        log.info("✅ ВАЛИДАЦИЯ УСПЕШНА: Свечи для тикера {} прошли все проверки", ticker);
//        return new ValidationResult(true, "Валидация успешна");
//    }

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

//    /**
//     * Класс для хранения ожидаемых параметров валидации
//     */
//    private static class ExpectedParameters {
//        final int candlesCount;
//        final long expectedOldestTime;
//        final long expectedNewestTime;
//
//        ExpectedParameters(int candlesCount, long expectedOldestTime, long expectedNewestTime) {
//            this.candlesCount = candlesCount;
//            this.expectedOldestTime = expectedOldestTime;
//            this.expectedNewestTime = expectedNewestTime;
//        }
//    }

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

    /**
     * Класс для хранения результата валидации консистентности таймштампов
     */
    private static class TimestampValidationResult {
        final boolean isValid;
        final String reason;
        final List<TimestampGap> gaps;

        TimestampValidationResult(boolean isValid, String reason, List<TimestampGap> gaps) {
            this.isValid = isValid;
            this.reason = reason;
            this.gaps = gaps;
        }
    }

    /**
     * Класс для описания пропуска во временных интервалах свечей
     */
    private static class TimestampGap {
        final long startTimestamp;
        final long endTimestamp;
        final int missedCandlesCount;
        final int startPosition;
        final int endPosition;

        TimestampGap(long startTimestamp, long endTimestamp, int missedCandlesCount, int startPosition, int endPosition) {
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
            this.missedCandlesCount = missedCandlesCount;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }

        public long getStartTimestamp() {
            return startTimestamp;
        }

        public long getEndTimestamp() {
            return endTimestamp;
        }

        public int getMissedCandlesCount() {
            return missedCandlesCount;
        }

        public int getStartPosition() {
            return startPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }
    }
}