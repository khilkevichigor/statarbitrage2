package com.example.core.services;

import com.example.shared.dto.TradeResult;
import com.example.shared.dto.ZScoreData;
import com.example.shared.dto.ZScoreParam;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryPointService {

    public void addEntryPoints(Pair tradingPair, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        if (zScoreData.getZScoreHistory() == null || zScoreData.getZScoreHistory().isEmpty()) {
            log.error("Z-score history is empty for pair {}", tradingPair.getPairName());
            // Or throw an exception, depending on desired behavior
            return;
        }
        ZScoreParam latestParam = zScoreData.getZScoreHistory().get(zScoreData.getZScoreHistory().size() - 1);
        tradingPair.setLongTickerEntryPrice(openLongTradeResult.getExecutionPrice());
        tradingPair.setShortTickerEntryPrice(openShortTradeResult.getExecutionPrice());
        tradingPair.setZScoreEntry(BigDecimal.valueOf(latestParam.getZscore()));
        tradingPair.setCorrelationEntry(BigDecimal.valueOf(latestParam.getCorrelation()));
        tradingPair.setAdfPvalueEntry(BigDecimal.valueOf(latestParam.getAdfpvalue()));
        tradingPair.setPValueEntry(BigDecimal.valueOf(latestParam.getPvalue()));
        tradingPair.setMeanEntry(BigDecimal.valueOf(latestParam.getMean()));
        tradingPair.setStdEntry(BigDecimal.valueOf(latestParam.getStd()));
        tradingPair.setSpreadEntry(BigDecimal.valueOf(latestParam.getSpread()));
        tradingPair.setAlphaEntry(BigDecimal.valueOf(latestParam.getAlpha()));
        tradingPair.setBetaEntry(BigDecimal.valueOf(latestParam.getBeta()));
        // Время входа
        tradingPair.setEntryTime(openLongTradeResult.getExecutionTime());

        log.debug("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z-скор = {}",
                tradingPair.getLongTicker(), tradingPair.getLongTickerEntryPrice(),
                tradingPair.getShortTicker(), tradingPair.getShortTickerEntryPrice(),
                tradingPair.getZScoreEntry());
    }


}
