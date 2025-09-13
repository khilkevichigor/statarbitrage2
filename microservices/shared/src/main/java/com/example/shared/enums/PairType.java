package com.example.shared.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы торговых пар в системе
 * Объединяет функциональность StablePair, CointPair и TradingPair
 */
@Getter
@RequiredArgsConstructor
public enum PairType {
    
    /**
     * Найденная стабильная пара (бывший StablePair)
     * - Результат скрининга потенциально стабильных пар
     * - Содержит результаты анализа стабильности и коинтеграции
     * - Может быть добавлена в мониторинг или использована для дальнейшего анализа
     */
    STABLE("Стабильная пара", "Найденная при скрининге стабильная пара"),
    
    /**
     * Коинтегрированная пара (бывший CointPair) 
     * - Пара прошедшая анализ коинтеграции
     * - Готова к переводу в активную торговлю
     * - Содержит статистические данные и параметры входа
     */
    COINTEGRATED("Коинтегрированная пара", "Пара прошедшая анализ коинтеграции"),
    
    /**
     * Активно торгуемая пара (бывший TradingPair)
     * - Пара с открытыми позициями
     * - Содержит полную торговую статистику
     * - Отслеживает P&L, изменения цен, события усреднения
     */
    TRADING("Торгуемая пара", "Активно торгуемая пара с открытыми позициями");
    
    private final String displayName;
    private final String description;
    
    /**
     * Получить PairType по строковому представлению (case-insensitive)
     */
    public static PairType fromString(String value) {
        if (value == null) {
            return null;
        }
        
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Проверить является ли тип стабильной парой
     */
    public boolean isStable() {
        return this == STABLE;
    }
    
    /**
     * Проверить является ли тип коинтегрированной парой  
     */
    public boolean isCointegrated() {
        return this == COINTEGRATED;
    }
    
    /**
     * Проверить является ли тип торгуемой парой
     */
    public boolean isTrading() {
        return this == TRADING;
    }
    
    /**
     * Получить список типов доступных для конверсии из данного типа
     */
    public PairType[] getConvertibleTypes() {
        return switch (this) {
            case STABLE -> new PairType[]{COINTEGRATED, TRADING};
            case COINTEGRATED -> new PairType[]{TRADING};
            case TRADING -> new PairType[]{COINTEGRATED}; // При закрытии позиций
        };
    }
    
    /**
     * Проверить может ли данный тип быть конвертирован в целевой тип
     */
    public boolean canConvertTo(PairType targetType) {
        for (PairType convertible : getConvertibleTypes()) {
            if (convertible == targetType) {
                return true;
            }
        }
        return false;
    }
}