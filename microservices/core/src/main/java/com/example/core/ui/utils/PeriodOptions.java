package com.example.core.ui.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Утилитарный класс для работы с периодами анализа
 */
public class PeriodOptions {
    
    /**
     * Получить все доступные периоды
     * @return Map где ключ и значение - одинаковые строки периода
     */
    public static Map<String, String> getAll() {
        Map<String, String> periods = new LinkedHashMap<>();
        periods.put("день", "день");
        periods.put("неделя", "неделя");
        periods.put("месяц", "месяц");
        periods.put("1 год", "1 год");
        periods.put("2 года", "2 года");
        periods.put("3 года", "3 года");
        return periods;
    }
    
    /**
     * Получить значение по умолчанию
     */
    public static String getDefault() {
        return "месяц";
    }
    
    /**
     * Расчет количества свечей для периода и таймфрейма
     */
    public static int calculateCandleLimit(String timeframe, String period) {
        int multiplier = switch (period.toLowerCase()) {
            case "день" -> 1;
            case "неделя" -> 7;
            case "месяц" -> 30;
            case "1 год" -> 365;
            case "2 года" -> 730;
            case "3 года" -> 1095;
            default -> 30;
        };

        int idealLimit = switch (timeframe) {
            case "1m" -> multiplier * 24 * 60; // минуты в день
            case "5m" -> multiplier * 24 * 12; // 5-минутки в день
            case "15m" -> multiplier * 24 * 4; // 15-минутки в день
            case "1h" -> multiplier * 24; // часы в день
            case "4h" -> multiplier * 6; // 4-часовки в день
            case "1D" -> multiplier; // дни
            case "1W" -> multiplier / 7; // недели
            case "1M" -> multiplier / 30; // месяцы
            default -> multiplier * 24; // По умолчанию часовки
        };
        
        // Минимум 100 свечей для качественного анализа
        return Math.max(100, idealLimit);
    }
}