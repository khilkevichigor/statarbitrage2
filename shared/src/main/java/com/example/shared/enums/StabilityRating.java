package com.example.shared.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Рейтинг стабильности пары
 */
@Getter
@RequiredArgsConstructor
public enum StabilityRating {
    
    EXCELLENT("EXCELLENT", "Отличная", 5),
    GOOD("GOOD", "Хорошая", 4),
    MARGINAL("MARGINAL", "Приемлемая", 3),
    POOR("POOR", "Плохая", 2),
    REJECTED("REJECTED", "Отклонена", 1),
    FAILED("FAILED", "Неудача", 0);
    
    /**
     * Значение для JSON сериализации (совместимость с Python API)
     */
    @JsonValue
    private final String jsonValue;
    
    /**
     * Русское описание
     */
    private final String description;
    
    /**
     * Числовой рейтинг для сортировки (больше = лучше)
     */
    private final int score;
    
    /**
     * Парсинг из строки (для обратной совместимости)
     */
    public static StabilityRating fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return FAILED;
        }
        
        String upperValue = value.trim().toUpperCase();
        for (StabilityRating rating : values()) {
            if (rating.jsonValue.equals(upperValue)) {
                return rating;
            }
        }
        
        // Fallback для неизвестных значений
        return FAILED;
    }
    
    /**
     * Проверяет, является ли рейтинг "хорошим" для торговли
     */
    public boolean isGoodForTrading() {
        return this == EXCELLENT || this == GOOD || this == MARGINAL;
    }
    
    /**
     * Проверяет, является ли рейтинг отличным или хорошим
     */
    public boolean isExcellentOrGood() {
        return this == EXCELLENT || this == GOOD;
    }
    
    @Override
    public String toString() {
        return jsonValue;
    }
}