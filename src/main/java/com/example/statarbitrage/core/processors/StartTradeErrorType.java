package com.example.statarbitrage.core.processors;

import com.example.statarbitrage.common.model.TradeStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы ошибок при начале нового трейда
 */
@Getter
@RequiredArgsConstructor
public enum StartTradeErrorType {
    Z_SCORE_BELOW_MINIMUM(TradeStatus.ERROR_500, "ZScore меньше минимального значения"),
    Z_SCORE_DATA_EMPTY(TradeStatus.ERROR_600, "ZScore данные пусты"),
    TICKERS_SWITCHED(TradeStatus.ERROR_400, "Лонг и шорт поменялись местами"),
    AUTO_TRADING_DISABLED(TradeStatus.ERROR_300, "Автотрейдинг отключен"),
    INSUFFICIENT_FUNDS(TradeStatus.ERROR_110, "Недостаточно средств в торговом депо"),
    TRADE_OPEN_FAILED(TradeStatus.ERROR_100, "Не удалось открыть арбитражную пару");

    private final TradeStatus status;
    private final String description;
}