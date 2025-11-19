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
     * Найденная пара для торговли
     * - Пара получила положительный Z-Score
     * - Готова к проверке фильтрами StartNewTradeProcessor
     * - Позиции еще не открыты
     */
    FETCHED("Найденная пара", "Пара найдена для торговли, позиции не открыты"),
    
    /**
     * Активно торгуемая пара
     * - Пара с открытыми позициями
     * - Содержит полную торговую статистику
     * - Отслеживает P&L, изменения цен, события усреднения
     */
    IN_TRADING("Торгуемая пара", "Активно торгуемая пара с открытыми позициями"),
    
    /**
     * Завершенная торговля
     * - Позиции закрыты
     * - Торговля завершена (успешно или по стоп-лоссу)
     * - Содержит финальную статистику
     */
    COMPLETED("Завершенная пара", "Торговля завершена, позиции закрыты"),
    
    /**
     * @deprecated Использовать IN_TRADING
     * Активно торгуемая пара (бывший TradingPair)
     */
    @Deprecated
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
        return this == IN_TRADING || this == TRADING;
    }
    
    /**
     * Проверить является ли тип найденной парой
     */
    public boolean isFetched() {
        return this == FETCHED;
    }
    
    /**
     * Проверить является ли тип завершенной парой
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }
}