package com.example.statarbitrage.common.dto;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.model.TradeResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePairDataRequest {
    private boolean isVirtual;
    private PairData pairData;
    private ZScoreData zScoreData;
    private Map<String, List<Candle>> candlesMap;
    private TradeResult tradeResultLong;
    private TradeResult tradeResultShort;
}
