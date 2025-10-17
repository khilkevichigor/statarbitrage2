package com.example.shared.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Централизованный сервис для работы с таймфреймами и периодами
 * Содержит все логику конвертации, валидации и расчетов
 */
@Slf4j
@Service
public class TimeframeAndPeriodService {

    // Все доступные таймфреймы в системе
    private static final List<String> ALL_TIMEFRAMES = List.of(
            "1m", "5m", "15m", "1H", "4H", "1D", "1W", "1M"
    );

    // Все доступные периоды в системе
    private static final List<String> ALL_PERIODS = List.of(
            "1 месяц", "2 месяца", "3 месяца", "4 месяца", "5 месяцев",
            "6 месяцев", "7 месяцев", "8 месяцев", "9 месяцев",
            "10 месяцев", "11 месяцев", "1 год"
    );

    // Карта для конвертации периодов в дни
    private static final Map<String, Integer> PERIOD_TO_DAYS;

    static {
        Map<String, Integer> periodMap = new LinkedHashMap<>();
        periodMap.put("1 месяц", 30);
        periodMap.put("2 месяца", 60);
        periodMap.put("3 месяца", 90);
        periodMap.put("4 месяца", 120);
        periodMap.put("5 месяцев", 150);
        periodMap.put("6 месяцев", 180);
        periodMap.put("7 месяцев", 210);
        periodMap.put("8 месяцев", 240);
        periodMap.put("9 месяцев", 270);
        periodMap.put("10 месяцев", 300);
        periodMap.put("11 месяцев", 330);
        periodMap.put("1 год", 365);
        PERIOD_TO_DAYS = Collections.unmodifiableMap(periodMap);
    }

    // Карта для конвертации дней обратно в периоды
    private static final Map<Integer, String> DAYS_TO_PERIOD = PERIOD_TO_DAYS.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    // Карта для получения Duration из таймфрейма
    private static final Map<String, Duration> TIMEFRAME_TO_DURATION = Map.of(
            "1m", Duration.ofMinutes(1),
            "5m", Duration.ofMinutes(5),
            "15m", Duration.ofMinutes(15),
            "1H", Duration.ofHours(1),
            "4H", Duration.ofHours(4),
            "1D", Duration.ofDays(1),
            "1W", Duration.ofDays(7),
            "1M", Duration.ofDays(30)
    );

    /**
     * Получить список всех доступных таймфреймов (API коды)
     */
    public List<String> getAllTimeframesList() {
        return new ArrayList<>(ALL_TIMEFRAMES);
    }

    /**
     * Получить список всех доступных периодов
     */
    public List<String> getAllPeriodsList() {
        return new ArrayList<>(ALL_PERIODS);
    }

    /**
     * Получить активные таймфреймы на основе глобальных настроек
     */
    public List<String> getActiveTimeframes(String activeTimeframesString) {
        if (activeTimeframesString == null || activeTimeframesString.trim().isEmpty()) {
            log.warn("⚠️ Активные таймфреймы не заданы, возвращаем 15m по умолчанию");
            return List.of("15m");
        }

        List<String> activeTimeframes = Arrays.stream(activeTimeframesString.split(","))
                .map(String::trim)
                .filter(tf -> !tf.isEmpty() && ALL_TIMEFRAMES.contains(tf))
                .collect(Collectors.toList());

        if (activeTimeframes.isEmpty()) {
            log.warn("⚠️ Не найдено валидных активных таймфреймов в '{}', возвращаем 15m по умолчанию",
                    activeTimeframesString);
            return List.of("15m");
        }

        log.debug("📊 Активные таймфреймы: {}", activeTimeframes);
        return activeTimeframes;
    }

    /**
     * Получить активные периоды на основе глобальных настроек
     */
    public List<String> getActivePeriods(String activePeriodsString) {
        if (activePeriodsString == null || activePeriodsString.trim().isEmpty()) {
            log.warn("⚠️ Активные периоды не заданы, возвращаем '1 год' по умолчанию");
            return List.of("1 год");
        }

        List<String> activePeriods = Arrays.stream(activePeriodsString.split(","))
                .map(String::trim)
                .filter(period -> !period.isEmpty() && ALL_PERIODS.contains(period))
                .collect(Collectors.toList());

        if (activePeriods.isEmpty()) {
            log.warn("⚠️ Не найдено валидных активных периодов в '{}', возвращаем '1 год' по умолчанию",
                    activePeriodsString);
            return List.of("1 год");
        }

        log.debug("📅 Активные периоды: {}", activePeriods);
        return activePeriods;
    }

    /**
     * Конвертировать период в дни
     */
    public Integer periodToDays(String period) {
        Integer days = PERIOD_TO_DAYS.get(period);
        if (days == null) {
            log.warn("⚠️ Неизвестный период '{}', возвращаем 365 дней (1 год)", period);
            return 365;
        }
        return days;
    }

    /**
     * Конвертировать дни в период
     */
    public String daysToPeriod(Integer days) {
        String period = DAYS_TO_PERIOD.get(days);
        if (period == null) {
            log.warn("⚠️ Неизвестное количество дней {}, возвращаем '1 год'", days);
            return "1 год";
        }
        return period;
    }

    /**
     * Получить Duration для таймфрейма
     */
    public Duration getTimeframeDuration(String timeframe) {
        Duration duration = TIMEFRAME_TO_DURATION.get(timeframe);
        if (duration == null) {
            log.warn("⚠️ Неизвестный таймфрейм '{}', возвращаем 15 минут", timeframe);
            return Duration.ofMinutes(15);
        }
        return duration;
    }

    /**
     * Проверить валидность таймфрейма
     */
    public boolean isValidTimeframe(String timeframe) {
        return timeframe != null && ALL_TIMEFRAMES.contains(timeframe);
    }

    /**
     * Проверить валидность периода
     */
    public boolean isValidPeriod(String period) {
        return period != null && ALL_PERIODS.contains(period);
    }

    /**
     * Конвертировать список активных таймфреймов в строку
     */
    public String timeframesToString(List<String> timeframes) {
        if (timeframes == null || timeframes.isEmpty()) {
            return "15m";
        }
        return String.join(",", timeframes);
    }

    /**
     * Конвертировать список активных периодов в строку
     */
    public String periodsToString(List<String> periods) {
        if (periods == null || periods.isEmpty()) {
            return "1 год";
        }
        return String.join(",", periods);
    }

    /**
     * Рассчитать оптимальное количество свечей для периода и таймфрейма
     */
    public int calculateOptimalCandleCount(String timeframe, String period) {
        Duration timeframeDuration = getTimeframeDuration(timeframe);
        int periodDays = periodToDays(period);

        // Рассчитываем количество свечей = дни * 24 часа * 60 минут / минуты в таймфрейме
        long timeframeMinutes = timeframeDuration.toMinutes();
        int totalMinutes = periodDays * 24 * 60;
        int candleCount = (int) (totalMinutes / timeframeMinutes);

        log.debug("🧮 Расчет свечей для ТФ {} и периода {}: {} дней = {} свечей",
                timeframe, period, periodDays, candleCount);

        return Math.max(candleCount, 100); // Минимум 100 свечей
    }

    /**
     * Получить рекомендуемый период EMA для таймфрейма
     */
    public int getRecommendedEmaPeriod(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "1m", "5m" -> 20;
            case "15m", "1h" -> 14;
            case "4h" -> 12;
            case "1d", "1w" -> 10;
            default -> 14;
        };
    }

    /**
     * Получить все доступные таймфреймы для UI с отображаемыми названиями
     */
    public Map<String, String> getAllTimeframes() {
        Map<String, String> timeframes = new LinkedHashMap<>();
        timeframes.put("1 минута", "1m");
        timeframes.put("5 минут", "5m");
        timeframes.put("15 минут", "15m");
        timeframes.put("1 час", "1H");
        timeframes.put("4 часа", "4H");
        timeframes.put("1 день", "1D");
        timeframes.put("1 неделя", "1W");
        timeframes.put("1 месяц", "1M");
        return timeframes;
    }

    /**
     * Получить все доступные периоды для UI
     */
    public Map<String, String> getAllPeriods() {
        Map<String, String> periods = new LinkedHashMap<>();
        for (String period : ALL_PERIODS) {
            periods.put(period, period);
        }
        return periods;
    }

    /**
     * Логирование текущих активных настроек
     */
    public void logActiveSettings(String activeTimeframes, String activePeriods) {
        log.info("🔧 Текущие глобальные настройки свечей:");
        log.info("📊 Активные таймфреймы: {}", getActiveTimeframes(activeTimeframes));
        log.info("📅 Активные периоды: {}", getActivePeriods(activePeriods));
    }
}