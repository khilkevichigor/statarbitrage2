package com.example.core.ui.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Утилитарный класс для работы с таймфреймами
 */
public class TimeframeOptions {
    
    /**
     * Получить все доступные таймфреймы
     * @return Map где ключ - отображаемое название, значение - API код
     */
    public static Map<String, String> getAll() {
        Map<String, String> timeframes = new LinkedHashMap<>();
        timeframes.put("1 минута", "1m");
        timeframes.put("5 минут", "5m");
        timeframes.put("15 минут", "15m");
        timeframes.put("1 час", "1h");
        timeframes.put("4 часа", "4h");
        timeframes.put("1 день", "1D");
        timeframes.put("1 неделя", "1W");
        timeframes.put("1 месяц", "1M");
        return timeframes;
    }
    
    /**
     * Получить отображаемое название по API коду
     */
    public static String getDisplayName(String apiCode) {
        return getAll().entrySet().stream()
                .filter(entry -> entry.getValue().equals(apiCode))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(apiCode);
    }
    
    /**
     * Получить API код по отображаемому названию
     */
    public static String getApiCode(String displayName) {
        return getAll().getOrDefault(displayName, displayName);
    }
    
    /**
     * Получить значение по умолчанию для UI
     */
    public static String getDefaultDisplayName() {
        return "1 день";
    }
    
    /**
     * Получить API код по умолчанию
     */
    public static String getDefaultApiCode() {
        return "1D";
    }
}