package com.example.core.schedulers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ошибок при выполнении шедулеров
 */
@Getter
@RequiredArgsConstructor
public enum SchedulerErrorType {
    SCHEDULER_ALREADY_RUNNING("Шедулер уже выполняется"),
    UPDATE_TRADES_FAILED("Ошибка при обновлении трейдов"),
    MAINTAIN_PAIRS_FAILED("Ошибка при поддержании пар"),
    TRADING_INTEGRATION_FAILED("Ошибка в торговой интеграции"),
    SETTINGS_LOAD_FAILED("Ошибка загрузки настроек"),
    INTERRUPTED_OPERATION("Операция была прервана"),
    WAIT_TIMEOUT("Превышено время ожидания");

    private final String description;
}