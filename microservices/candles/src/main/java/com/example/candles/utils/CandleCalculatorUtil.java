package com.example.candles.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилитный класс для расчета количества свечей по таймфрейму и периоду
 * <p>
 * Поддерживаемые периоды: день, неделя, месяц, 6 месяцев, 1 год, 2 года, 3 года
 * Поддерживаемые таймфреймы: 1m, 5m, 15m, 1H, 4H, 1D, 1W, 1M
 * <p>
 * Погрешности:
 * - Для минутных и часовых ТФ: ±1 день (допустимо отклонение)
 * - Для остальных ТФ: точное соответствие 1:1
 */
@Slf4j
public class CandleCalculatorUtil {

    // Константы для расчетов
    private static final int MINUTES_PER_HOUR = 60;
    private static final int HOURS_PER_DAY = 24;
    private static final int DAYS_PER_WEEK = 7;
    private static final int DAYS_PER_MONTH = 30; // Среднее количество дней в месяце
    private static final int DAYS_PER_YEAR = 365; // Среднее количество дней в году
    private static final int WEEKS_PER_MONTH = 4; // Примерное количество недель в месяце
    private static final int WEEKS_PER_YEAR = 52; // Количество недель в году
    private static final int MONTHS_PER_YEAR = 12;

    // Паттерн для парсинга периодов с числами
    private static final Pattern PERIOD_PATTERN = Pattern.compile("(\\d+)\\s*(.*)", Pattern.CASE_INSENSITIVE);

    /**
     * Основной метод для расчета количества свечей
     */
    public static int calculateCandlesCount(String ticker, String timeframe, String period) {
        log.debug("🧮 РАСЧЕТ СВЕЧЕЙ для {}: timeframe={}, period={}", ticker, timeframe, period);

        try {
            int periodDays = parsePeriodToDays(period);
            int candlesCount = calculateCandlesByTimeframe(timeframe, periodDays);

            log.info("✅ РЕЗУЛЬТАТ РАСЧЕТА для {}: {} свечей для периода '{}' ({} дней) с таймфреймом {}",
                    ticker, candlesCount, period, periodDays, timeframe);
            return candlesCount;

        } catch (Exception e) {
            log.error("❌ ОШИБКА РАСЧЕТА для {}: Ошибка при расчете свечей для timeframe={}, period={}: {}",
                    ticker, timeframe, period, e.getMessage());
            // Возвращаем разумное значение по умолчанию (1 месяц, 1H)
            return DAYS_PER_MONTH * HOURS_PER_DAY;
        }
    }

    /**
     * Парсит период в количество дней
     */
    private static int parsePeriodToDays(String period) {
        if (period == null || period.trim().isEmpty()) {
            throw new IllegalArgumentException("Период не может быть пустым");
        }

        String normalizedPeriod = period.toLowerCase().trim();

        // Обрабатываем периоды с числами (2 года, 3 года, 6 месяцев)
        Matcher matcher = PERIOD_PATTERN.matcher(normalizedPeriod);
        if (matcher.matches()) {
            int number = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).trim();

            return switch (unit) {
                case "год", "года", "лет", "year", "years" -> number * DAYS_PER_YEAR;
                case "месяц", "месяца", "месяцев", "month", "months" -> number * DAYS_PER_MONTH;
                case "неделя", "недели", "недель", "week", "weeks" -> number * DAYS_PER_WEEK;
                case "день", "дня", "дней", "day", "days" -> number;
                default -> throw new IllegalArgumentException("Неизвестная единица времени: " + unit);
            };
        }

        // Обрабатываем периоды без чисел
        return switch (normalizedPeriod) {
            case "день", "day" -> 1;
            case "неделя", "week" -> DAYS_PER_WEEK;
            case "месяц", "month" -> DAYS_PER_MONTH;
            case "год", "year" -> DAYS_PER_YEAR;

            // Специальные случаи
            case "1 год", "1year" -> DAYS_PER_YEAR;
            case "6 месяцев", "6months" -> 6 * DAYS_PER_MONTH;

            default -> throw new IllegalArgumentException("Неизвестный период: " + period);
        };
    }

    /**
     * Вычисляет количество свечей по таймфрейму и количеству дней
     */
    private static int calculateCandlesByTimeframe(String timeframe, int periodDays) {
        return switch (timeframe) {
            // Минутные таймфреймы
            case "1m" -> periodDays * HOURS_PER_DAY * MINUTES_PER_HOUR;              // дни * 24 * 60
            case "5m" -> periodDays * HOURS_PER_DAY * (MINUTES_PER_HOUR / 5);        // дни * 24 * 12
            case "15m" -> periodDays * HOURS_PER_DAY * (MINUTES_PER_HOUR / 15);      // дни * 24 * 4

            // Часовые таймфреймы
            case "1H" -> periodDays * HOURS_PER_DAY;                                 // дни * 24
            case "4H" -> periodDays * (HOURS_PER_DAY / 4);                          // дни * 6

            // Дневные и выше
            case "1D" -> periodDays;                                                // дни * 1
            case "1W" -> periodDays / DAYS_PER_WEEK;                                // дни / 7
            case "1M" -> periodDays / DAYS_PER_MONTH;                               // дни / 30

            default -> throw new IllegalArgumentException("Неизвестный таймфрейм: " + timeframe);
        };
    }

    /**
     * Возвращает допустимую погрешность для валидации количества свечей
     */
    public static int getAllowedDifference(String timeframe, int expectedCount) {
        return switch (timeframe) {
            // Для минутных и часовых таймфреймов: погрешность 1 день
            case "1m" -> HOURS_PER_DAY * MINUTES_PER_HOUR;              // 1440 минут в дне
            case "5m" -> HOURS_PER_DAY * (MINUTES_PER_HOUR / 5);        // 288 пятиминуток в дне  
            case "15m" -> HOURS_PER_DAY * (MINUTES_PER_HOUR / 15);      // 96 пятнадцатиминуток в дне
            case "1H" -> HOURS_PER_DAY;                                 // 24 часа в дне
            case "4H" -> HOURS_PER_DAY / 4;                             // 6 четырехчасовок в дне

            // Для остальных таймфреймов: точное соответствие (0 погрешности)
            case "1D", "1W", "1M" -> 0;

            default -> {
                log.warn("⚠️ НЕИЗВЕСТНЫЙ ТАЙМФРЕЙМ для погрешности: {}, используем 0", timeframe);
                yield 0;
            }
        };
    }

    /**
     * Проверяет, находится ли количество свечей в допустимых пределах
     */
    public static boolean isValidCandlesCount(String timeframe, int expectedCount, int actualCount) {
        int allowedDifference = getAllowedDifference(timeframe, expectedCount);
        int actualDifference = Math.abs(actualCount - expectedCount);

        boolean isValid = actualDifference <= allowedDifference;

        if (!isValid) {
            log.warn("⚠️ ВАЛИДАЦИЯ КОЛИЧЕСТВА: Отклонение {} превышает допустимое {} для таймфрейма {}",
                    actualDifference, allowedDifference, timeframe);
        } else if (actualDifference > 0) {
            log.debug("ℹ️ ВАЛИДАЦИЯ КОЛИЧЕСТВА: Отклонение {} в пределах нормы (допустимо {}) для таймфрейма {}",
                    actualDifference, allowedDifference, timeframe);
        }

        return isValid;
    }

    /**
     * Возвращает описание допустимой погрешности для таймфрейма
     */
    public static String getToleranceDescription(String timeframe) {
        return switch (timeframe) {
            case "1m", "5m", "15m", "1H", "4H" -> "±1 день";
            case "1D", "1W", "1M" -> "точное соответствие";
            default -> "неизвестно";
        };
    }

    /**
     * Метод для тестирования - возвращает подробную информацию о расчете
     */
    public static String getCalculationDetails(String timeframe, String period) {
        try {
            int periodDays = parsePeriodToDays(period);
            int candlesCount = calculateCandlesByTimeframe(timeframe, periodDays);
            int allowedDifference = getAllowedDifference(timeframe, candlesCount);
            String tolerance = getToleranceDescription(timeframe);

            return String.format("""
                    📊 ДЕТАЛИ РАСЧЕТА:
                    Период: %s (%d дней)
                    Таймфрейм: %s
                    Ожидаемое количество свечей: %d
                    Допустимая погрешность: %d (%s)
                    """, period, periodDays, timeframe, candlesCount, allowedDifference, tolerance);

        } catch (Exception e) {
            return "❌ Ошибка расчета: " + e.getMessage();
        }
    }
}