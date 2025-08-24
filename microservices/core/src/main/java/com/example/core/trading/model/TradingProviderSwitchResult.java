package com.example.core.trading.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Результат переключения провайдера торговли
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradingProviderSwitchResult {

    /**
     * Успешность переключения
     */
    private boolean success;

    /**
     * Тип ошибки (если есть)
     */
    private SwitchErrorType errorType;

    /**
     * Подробное сообщение об ошибке
     */
    private String errorMessage;

    /**
     * Сообщение для пользователя
     */
    private String userMessage;

    /**
     * Рекомендации по исправлению
     */
    private String recommendation;

    /**
     * Создание успешного результата
     */
    public static TradingProviderSwitchResult success() {
        return TradingProviderSwitchResult.builder()
                .success(true)
                .build();
    }

    /**
     * Создание результата с ошибкой
     */
    public static TradingProviderSwitchResult failure(SwitchErrorType errorType,
                                                      String errorMessage,
                                                      String userMessage,
                                                      String recommendation) {
        return TradingProviderSwitchResult.builder()
                .success(false)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .userMessage(userMessage)
                .recommendation(recommendation)
                .build();
    }

    /**
     * Типы ошибок при переключении провайдера
     */
    public enum SwitchErrorType {
        /**
         * Провайдер не реализован
         */
        PROVIDER_NOT_IMPLEMENTED,

        /**
         * Отсутствует конфигурация (API ключи)
         */
        CONFIGURATION_MISSING,

        /**
         * Ошибка подключения к API
         */
        CONNECTION_ERROR,

        /**
         * Недействительные API ключи
         */
        INVALID_CREDENTIALS,

        /**
         * Внутренняя ошибка системы
         */
        INTERNAL_ERROR
    }
}