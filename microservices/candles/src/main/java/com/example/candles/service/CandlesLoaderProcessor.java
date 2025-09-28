package com.example.candles.service;

import com.example.candles.client.OkxFeignClient;
import com.example.shared.dto.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Сервис-процессор для загрузки свечей с OKX и сохранения в БД
 * 
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
            List<Candle> candles = loadCandlesFromOkx(ticker, timeframe, candlesCount);
            if (candles == null || candles.isEmpty()) {
                log.error("❌ ОШИБКА: Не удалось загрузить свечи для тикера {}", ticker);
                return 0;
            }
            
            // Шаг 3: Валидируем загруженные данные
            boolean isValid = validateLoadedCandles(candles, untilDate, timeframe, period, candlesCount);
            if (!isValid) {
                log.error("❌ ВАЛИДАЦИЯ: Загруженные свечи не прошли валидацию для тикера {}", ticker);
                return 0;
            }
            
            // Шаг 4: Сохраняем свечи в БД
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
        log.info("🔢 РАСЧЕТ СВЕЧЕЙ: timeframe={}, period={}", timeframe, period);
        
        // Получаем количество единиц времени в периоде
        int periodUnits = parsePeriod(period);
        
        // Вычисляем количество свечей в зависимости от таймфрейма
        int candlesCount = switch (timeframe) {
            case "1m" -> periodUnits * 365 * 24 * 60;        // минуты в году
            case "5m" -> periodUnits * 365 * 24 * 12;        // 5-минутки в году
            case "15m" -> periodUnits * 365 * 24 * 4;        // 15-минутки в году  
            case "1H" -> periodUnits * 365 * 24;             // часы в году
            case "4H" -> periodUnits * 365 * 6;              // 4-часовки в году
            case "1D" -> periodUnits * 365;                  // дни в году
            case "1W" -> periodUnits * 52;                   // недели в году
            case "1M" -> periodUnits * 12;                   // месяцы в году (если это месячный ТФ)
            default -> {
                log.warn("⚠️ НЕИЗВЕСТНЫЙ ТАЙМФРЕЙМ: {}, используем расчет для 1H", timeframe);
                yield periodUnits * 365 * 24;
            }
        };
        
        log.info("✅ РЕЗУЛЬТАТ РАСЧЕТА: {} свечей для периода {} лет с таймфреймом {}", 
                candlesCount, periodUnits, timeframe);
        return candlesCount;
    }
    
    /**
     * Парсит период типа "1year", "6months", "30days" в количество лет
     */
    private int parsePeriod(String period) {
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
     * Загружает свечи с OKX
     */
    private List<Candle> loadCandlesFromOkx(String ticker, String timeframe, int candlesCount) {
        log.info("📡 OKX ЗАПРОС: Загружаем {} свечей для тикера {} с таймфреймом {}", 
                candlesCount, ticker, timeframe);
        
        try {
            List<Candle> candles = okxFeignClient.getCandles(ticker, timeframe, candlesCount);
            
            if (candles != null && !candles.isEmpty()) {
                log.info("✅ OKX ОТВЕТ: Получено {} свечей для тикера {}", candles.size(), ticker);
            } else {
                log.warn("⚠️ OKX ОТВЕТ: Получен пустой ответ для тикера {}", ticker);
            }
            
            return candles;
            
        } catch (Exception e) {
            log.error("❌ OKX ОШИБКА: Ошибка при загрузке свечей для тикера {}: {}", ticker, e.getMessage(), e);
            return null;
        }
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
            long oldestTime = candles.get(candles.size() - 1).getTimestamp(); // Последняя = самая старая
            long newestTime = candles.get(0).getTimestamp(); // Первая = самая новая
            
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
}