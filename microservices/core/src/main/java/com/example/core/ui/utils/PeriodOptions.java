package com.example.core.ui.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Утилитарный класс для работы с периодами анализа
 */
public class PeriodOptions {

    /**
     * Получить все доступные периоды
     *
     * @return Map где ключ и значение - одинаковые строки периода
     */
    public static Map<String, String> getAll() {
        Map<String, String> periods = new LinkedHashMap<>();
        periods.put("1 месяц", "1 месяц");
        periods.put("2 месяца", "2 месяца");
        periods.put("3 месяца", "3 месяца");
        periods.put("4 месяца", "4 месяца");
        periods.put("5 месяцев", "5 месяцев");
        periods.put("6 месяцев", "6 месяцев");
        periods.put("7 месяцев", "7 месяцев");
        periods.put("8 месяцев", "8 месяцев");
        periods.put("9 месяцев", "9 месяцев");
        periods.put("10 месяцев", "10 месяцев");
        periods.put("11 месяцев", "11 месяцев");
        periods.put("1 год", "1 год");
        return periods;
    }

    /**
     * Получить значение по умолчанию
     */
    public static String getDefault() {
        return "1 месяц";
    }

    /**
     * Расчет количества свечей для периода и таймфрейма
     */
    public static int calculateCandleLimit(String timeframe, String period) {
        int multiplier = switch (period.toLowerCase()) {
            case "1 месяц" -> 30;
            case "2 месяца" -> 60;
            case "3 месяца" -> 90;
            case "4 месяца" -> 120;
            case "5 месяцев" -> 150;
            case "6 месяцев" -> 180;
            case "7 месяцев" -> 210;
            case "8 месяцев" -> 240;
            case "9 месяцев" -> 270;
            case "10 месяцев" -> 300;
            case "11 месяцев" -> 330;
            case "1 год" -> 365;
            default -> 365;
        };

        int idealLimit = switch (timeframe) {
            case "1m" -> multiplier * 24 * 60; // 1-минутки в день
            case "5m" -> multiplier * 24 * 12; // 5-минутки в день
            case "15m" -> multiplier * 24 * 4; // 15-минутки в день
            case "1H" -> multiplier * 24; // часы в день
            case "4H" -> multiplier * 6; // 4-часовки в день
            case "1D" -> multiplier; // дни
            case "1W" -> multiplier / 7; // недели
            case "1M" -> multiplier / 30; // месяцы
            default -> multiplier * 24 * 4; // По умолчанию 15-минутки
        };

        // Минимум 100 свечей для качественного анализа
        return Math.max(100, idealLimit);
    }
}