package com.example.candles.service;

import com.example.candles.repositories.CachedCandleRepository;
import com.example.shared.dto.Candle;
import com.example.shared.models.CachedCandle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис-процессор для получения свечей из кэша с валидацией
 * 
 * Принимает параметры:
 * - биржа (exchange)
 * - тикер (ticker)
 * - дата ДО (untilDate) - обрезанная до начала дня в формате 2025-09-27T00:00:00Z
 * - таймфрейм (timeframe) в формате 1H, 1D, 1m...
 * - период (period) в виде "1year", "6months"...
 * 
 * Валидирует полученные свечи по количеству и временному диапазону.
 * При некорректных данных автоматически догружает через CandlesLoaderProcessor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheValidatedCandlesProcessor {
    
    private final CachedCandleRepository cachedCandleRepository;
    private final CandlesLoaderProcessor candlesLoaderProcessor;
    
    /**
     * Главный публичный метод для получения валидированных свечей из кэша
     */
    public List<Candle> getValidatedCandlesFromCache(String exchange, String ticker, String untilDate, String timeframe, String period) {
        log.info("🔍 КЭШ ЗАПРОС: Получаем свечи для тикера {} на бирже {}", ticker, exchange);
        log.info("📊 ПАРАМЕТРЫ: untilDate={}, timeframe={}, period={}", untilDate, timeframe, period);
        
        try {
            // Шаг 1: Вычисляем ожидаемые параметры
            ExpectedParameters expected = calculateExpectedParameters(untilDate, timeframe, period);
            log.info("🎯 ОЖИДАНИЯ: {} свечей в диапазоне {} - {}", 
                    expected.candlesCount, formatTimestamp(expected.expectedOldestTime), formatTimestamp(expected.expectedNewestTime));
            
            // Шаг 2: Получаем свечи из кэша
            List<Candle> cachedCandles = getCandlesFromCache(exchange, ticker, timeframe, expected.candlesCount);
            
            // Шаг 3: Валидируем полученные свечи
            ValidationResult validationResult = validateCachedCandles(cachedCandles, expected, ticker);
            
            // Шаг 4: Если данные некорректны - догружаем
            if (!validationResult.isValid) {
                log.warn("⚠️ ВАЛИДАЦИЯ ПРОВАЛЕНА: {}", validationResult.reason);
                log.info("🔄 ДОГРУЗКА: Запускаем загрузку свежих данных для тикера {}", ticker);
                
                // Догружаем свежие данные
                int loadedCount = candlesLoaderProcessor.loadAndSaveCandles(exchange, ticker, untilDate, timeframe, period);
                if (loadedCount > 0) {
                    log.info("✅ ДОГРУЗКА ЗАВЕРШЕНА: Загружено {} свечей, повторно получаем из кэша", loadedCount);
                    
                    // Повторно получаем из кэша после догрузки
                    cachedCandles = getCandlesFromCache(exchange, ticker, timeframe, expected.candlesCount);
                    validationResult = validateCachedCandles(cachedCandles, expected, ticker);
                    
                    if (!validationResult.isValid) {
                        log.error("❌ ПОВТОРНАЯ ВАЛИДАЦИЯ ПРОВАЛЕНА: {} для тикера {}", validationResult.reason, ticker);
                        return List.of(); // Возвращаем пустой список
                    }
                } else {
                    log.error("❌ ДОГРУЗКА ПРОВАЛЕНА: Не удалось загрузить данные для тикера {}", ticker);
                    return List.of(); // Возвращаем пустой список
                }
            }
            
            // Шаг 5: Возвращаем валидированные свечи
            log.info("✅ КЭШ РЕЗУЛЬТАТ: Возвращаем {} валидированных свечей для тикера {}", cachedCandles.size(), ticker);
            return cachedCandles;
            
        } catch (Exception e) {
            log.error("💥 КЭШ ОШИБКА: Ошибка при получении свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Вычисляет ожидаемые параметры для валидации
     */
    private ExpectedParameters calculateExpectedParameters(String untilDate, String timeframe, String period) {
        log.info("📐 РАСЧЕТ ПАРАМЕТРОВ: untilDate={}, timeframe={}, period={}", untilDate, timeframe, period);
        
        // Парсим дату "до"
        long untilTimestamp = parseUntilDate(untilDate);
        
        // Вычисляем количество свечей
        int candlesCount = calculateCandlesCount(timeframe, period);
        
        // Вычисляем ожидаемое время старейшей свечи
        long timeframeDurationMs = getTimeframeDurationInMillis(timeframe);
        long expectedOldestTime = untilTimestamp - (candlesCount * timeframeDurationMs);
        
        // Новейшая свеча должна быть близка к untilDate
        long expectedNewestTime = untilTimestamp;
        
        return new ExpectedParameters(candlesCount, expectedOldestTime, expectedNewestTime);
    }
    
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
    
    /**
     * Вычисляет количество свечей исходя из таймфрейма и периода
     */
    private int calculateCandlesCount(String timeframe, String period) {
        // Получаем количество лет в периоде
        int periodYears = parsePeriodToYears(period);
        
        // Вычисляем количество свечей в зависимости от таймфрейма
        return switch (timeframe) {
            case "1m" -> periodYears * 365 * 24 * 60;        // минуты в году
            case "5m" -> periodYears * 365 * 24 * 12;        // 5-минутки в году
            case "15m" -> periodYears * 365 * 24 * 4;        // 15-минутки в году  
            case "1H" -> periodYears * 365 * 24;             // часы в году
            case "4H" -> periodYears * 365 * 6;              // 4-часовки в году
            case "1D" -> periodYears * 365;                  // дни в году
            case "1W" -> periodYears * 52;                   // недели в году
            case "1M" -> periodYears * 12;                   // месяцы в году (если это месячный ТФ)
            default -> {
                log.warn("⚠️ НЕИЗВЕСТНЫЙ ТАЙМФРЕЙМ: {}, используем расчет для 1H", timeframe);
                yield periodYears * 365 * 24;
            }
        };
    }
    
    /**
     * Парсит период в количество лет
     */
    private int parsePeriodToYears(String period) {
        period = period.toLowerCase().trim();
        
        if (period.contains("1 год")) {
            String number = period.replaceAll("[^0-9]", "");
            return Integer.parseInt(number.isEmpty() ? "1" : number);
        } else if (period.contains("месяц")) {
            String number = period.replaceAll("[^0-9]", "");
            int months = Integer.parseInt(number.isEmpty() ? "6" : number);
            return Math.max(1, months / 12); // Переводим в года, минимум 1 год
        } else if (period.contains("день")) {
            String number = period.replaceAll("[^0-9]", "");
            int days = Integer.parseInt(number.isEmpty() ? "365" : number);
            return Math.max(1, days / 365); // Переводим в года, минимум 1 год
        } else {
            log.warn("⚠️ НЕИЗВЕСТНЫЙ ПЕРИОД: {}, используем 1 год", period);
            return 1;
        }
    }
    
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
     * Получает свечи из кэша
     */
    private List<Candle> getCandlesFromCache(String exchange, String ticker, String timeframe, int limit) {
        log.info("🗃️ КЭШ ЗАПРОС: Получаем {} свечей для тикера {} из кэша", limit, ticker);
        
        try {
            // Получаем последние N свечей из кэша
            List<CachedCandle> cachedCandles = cachedCandleRepository
                    .findLatestByTickerTimeframeExchange(ticker, timeframe, exchange, PageRequest.of(0, limit));
            
            // Конвертируем в Candle и сортируем по времени (старые первые)
            List<Candle> candles = cachedCandles.stream()
                    .map(CachedCandle::toCandle)
                    .sorted(Comparator.comparingLong(Candle::getTimestamp))
                    .collect(Collectors.toList());
            
            log.info("✅ КЭШ ОТВЕТ: Получено {} свечей для тикера {} из кэша", candles.size(), ticker);
            return candles;
            
        } catch (Exception e) {
            log.error("❌ КЭШ ОШИБКА: Ошибка получения свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Валидирует полученные из кэша свечи
     */
    private ValidationResult validateCachedCandles(List<Candle> candles, ExpectedParameters expected, String ticker) {
        log.info("🔍 ВАЛИДАЦИЯ КЭШа: Проверяем {} свечей для тикера {}", candles.size(), ticker);
        
        // Проверка 1: Количество свечей
        if (candles.size() != expected.candlesCount) {
            String reason = String.format("Неверное количество свечей: ожидалось %d, получено %d", 
                    expected.candlesCount, candles.size());
            log.warn("⚠️ ВАЛИДАЦИЯ КОЛИЧЕСТВА: {}", reason);
            return new ValidationResult(false, reason);
        }
        
        // Проверка 2: Временной диапазон (только если есть свечи)
        if (!candles.isEmpty()) {
            long actualOldestTime = candles.get(0).getTimestamp();          // Первая = самая старая (сортированы по возрастанию)
            long actualNewestTime = candles.get(candles.size() - 1).getTimestamp(); // Последняя = самая новая
            
            log.info("📅 ФАКТИЧЕСКИЙ ДИАПАЗОН: {} - {}", 
                    formatTimestamp(actualOldestTime), formatTimestamp(actualNewestTime));
            log.info("📅 ОЖИДАЕМЫЙ ДИАПАЗОН: {} - {}", 
                    formatTimestamp(expected.expectedOldestTime), formatTimestamp(expected.expectedNewestTime));
            
            // Допускаем погрешность в 1% от общего периода
            long totalPeriod = expected.expectedNewestTime - expected.expectedOldestTime;
            long allowedDifference = Math.max(totalPeriod / 100, 60 * 60 * 1000L); // Минимум 1 час
            
            // Проверяем старейшую свечу
            long oldestTimeDiff = Math.abs(actualOldestTime - expected.expectedOldestTime);
            if (oldestTimeDiff > allowedDifference) {
                String reason = String.format("Диапазон съехал: старейшая свеча %s не соответствует ожидаемой %s (разница %d мс)", 
                        formatTimestamp(actualOldestTime), formatTimestamp(expected.expectedOldestTime), oldestTimeDiff);
                log.warn("⚠️ ВАЛИДАЦИЯ ДИАПАЗОНА: {}", reason);
                return new ValidationResult(false, reason);
            }
            
            // Проверяем новейшую свечу
            long newestTimeDiff = Math.abs(actualNewestTime - expected.expectedNewestTime);
            if (newestTimeDiff > allowedDifference) {
                String reason = String.format("Диапазон съехал: новейшая свеча %s не соответствует ожидаемой %s (разница %d мс)", 
                        formatTimestamp(actualNewestTime), formatTimestamp(expected.expectedNewestTime), newestTimeDiff);
                log.warn("⚠️ ВАЛИДАЦИЯ ДИАПАЗОНА: {}", reason);
                return new ValidationResult(false, reason);
            }
        }
        
        log.info("✅ ВАЛИДАЦИЯ УСПЕШНА: Свечи для тикера {} прошли все проверки", ticker);
        return new ValidationResult(true, "Валидация успешна");
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
     * Класс для хранения ожидаемых параметров валидации
     */
    private static class ExpectedParameters {
        final int candlesCount;
        final long expectedOldestTime;
        final long expectedNewestTime;
        
        ExpectedParameters(int candlesCount, long expectedOldestTime, long expectedNewestTime) {
            this.candlesCount = candlesCount;
            this.expectedOldestTime = expectedOldestTime;
            this.expectedNewestTime = expectedNewestTime;
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