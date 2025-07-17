package com.example.statarbitrage.core.dto;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.trading.model.TradeResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Модель свечи для тестирования
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePairDataRequest {
    private boolean isAddEntryPoints;
    private PairData pairData;
    private ZScoreData zScoreData;
    private Map<String, List<Candle>> candlesMap;
    private TradeResult tradeResultLong;
    private TradeResult tradeResultShort;
    private boolean isUpdateChanges;
    private boolean isVirtual;
    private boolean isUpdateTradeLog;
    private Settings settings;
}