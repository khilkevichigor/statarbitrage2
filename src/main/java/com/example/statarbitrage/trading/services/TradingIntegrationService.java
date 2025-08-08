package com.example.statarbitrage.trading.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.trading.interfaces.TradingProviderType;
import com.example.statarbitrage.trading.model.ArbitragePairTradeInfo;
import com.example.statarbitrage.trading.model.Portfolio;
import com.example.statarbitrage.trading.model.Positioninfo;
import com.example.statarbitrage.trading.model.TradingProviderSwitchResult;
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

    void removePairFromLocalStorage(PairData pairData);

    /**
     * Обновление цен и PnL для всех активных пар - СИНХРОННО
     */
    void updateAllPositions();

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