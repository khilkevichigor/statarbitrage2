package com.example.core.trading.services;

import com.example.core.trading.interfaces.TradingProviderType;
import com.example.shared.dto.ArbitragePairTradeInfo;
import com.example.shared.dto.Portfolio;
import com.example.shared.dto.Positioninfo;
import com.example.shared.dto.TradingProviderSwitchResult;
import com.example.shared.models.Settings;
import com.example.shared.models.Pair;
import org.springframework.stereotype.Service;

/**
 * Сервис для интеграции новой торговой системы с существующей системой статарбитража
 */
@Service
public interface TradingIntegrationService {

    /**
     * Открытие пары позиций для статарбитража - СИНХРОННО
     */
    ArbitragePairTradeInfo openArbitragePair(Pair tradingPair, Settings settings);

    /**
     * Закрытие пары позиций - СИНХРОННО
     */
    ArbitragePairTradeInfo closeArbitragePair(Pair tradingPair);

    /**
     * Проверка что позиции действительно закрыты на бирже с получением PnL
     */
    Positioninfo verifyPositionsClosed(Pair tradingPair);

    /**
     * Получение актуальной информации по открытым позициям для обновления changes
     */
    Positioninfo getOpenPositionsInfo(Pair tradingPair);

    /**
     * Получение актуальной информации по позициям для пары
     */
    Positioninfo getPositionInfo(Pair tradingPair);

    void deletePositions(Pair tradingPair);

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