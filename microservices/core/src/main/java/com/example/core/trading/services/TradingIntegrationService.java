package com.example.core.trading.services;

import com.example.core.trading.interfaces.TradingProviderType;
import com.example.shared.models.*;
import org.springframework.stereotype.Service;

/**
 * Сервис для интеграции новой торговой системы с существующей системой статарбитража
 */
@Service
public interface TradingIntegrationService {

    /**
     * Открытие пары позиций для статарбитража - СИНХРОННО
     */
    ArbitragePairTradeInfo openArbitragePair(PairData pairData, Settings settings);

    /**
     * Закрытие пары позиций - СИНХРОННО
     */
    ArbitragePairTradeInfo closeArbitragePair(PairData pairData);

    /**
     * Проверка что позиции действительно закрыты на бирже с получением PnL
     */
    Positioninfo verifyPositionsClosed(PairData pairData);

    /**
     * Получение актуальной информации по открытым позициям для обновления changes
     */
    Positioninfo getOpenPositionsInfo(PairData pairData);

    /**
     * Получение актуальной информации по позициям для пары
     */
    Positioninfo getPositionInfo(PairData pairData);

    void deletePositions(PairData pairData);

    /**
     * Получение информации о портфолио
     */
    Portfolio getPortfolioInfo();

    /**
     * Проверка, достаточно ли средств для новой пары
     */
    boolean canOpenNewPair(Settings settings);

    /**
     * Переключение режима торговли с детальной информацией
     */
    TradingProviderSwitchResult switchTradingModeWithDetails(TradingProviderType providerType);

    /**
     * Получение текущего режима торговли
     */
    TradingProviderType getCurrentTradingMode();
}