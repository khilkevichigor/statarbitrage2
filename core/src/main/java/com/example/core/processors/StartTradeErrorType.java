package com.example.core.processors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ошибок при начале нового трейда
 */
@Getter
@RequiredArgsConstructor
public enum StartTradeErrorType {
    Z_SCORE_BELOW_MINIMUM("ZScore меньше минимального значения"),
    Z_SCORE_DATA_EMPTY("ZScore данные пусты"),
    TICKERS_SWITCHED("Лонг и шорт поменялись местами"),
    AUTO_TRADING_DISABLED("Автотрейдинг отключен"),
    ZSCORE_DECLINE_FILTER_FAILED("Фильтр снижения zScore не прошёл"),
    INSUFFICIENT_FUNDS("Недостаточно средств в торговом депо"),
    TRADE_OPEN_FAILED("Не удалось открыть арбитражную пару");

    private final String description;
}