package com.example.core.services;

import com.example.shared.dto.TradeResult;
import com.example.shared.dto.ZScoreData;
import com.example.shared.dto.ZScoreParam;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryPointService {

    public void addEntryPoints(TradingPair tradingPair, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        if (zScoreData.getZScoreHistory() == null || zScoreData.getZScoreHistory().isEmpty()) {
            log.error("Z-score history is empty for pair {}", tradingPair.getPairName());
            // Or throw an exception, depending on desired behavior
            return;
        }
        ZScoreParam latestParam = zScoreData.getZScoreHistory().get(zScoreData.getZScoreHistory().size() - 1);
        tradingPair.setLongTickerEntryPrice(openLongTradeResult.getExecutionPrice().doubleValue());
        tradingPair.setShortTickerEntryPrice(openShortTradeResult.getExecutionPrice().doubleValue());
        tradingPair.setZScoreEntry(latestParam.getZscore());
        tradingPair.setCorrelationEntry(latestParam.getCorrelation());
        tradingPair.setAdfPvalueEntry(latestParam.getAdfpvalue());
        tradingPair.setPValueEntry(latestParam.getPvalue());
        tradingPair.setMeanEntry(latestParam.getMean());
        tradingPair.setStdEntry(latestParam.getStd());
        tradingPair.setSpreadEntry(latestParam.getSpread());
        tradingPair.setAlphaEntry(latestParam.getAlpha());
        tradingPair.setBetaEntry(latestParam.getBeta());
        // Время входа
        tradingPair.setEntryTime(openLongTradeResult.getExecutionTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000);

        log.debug("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z-скор = {}",
                tradingPair.getLongTicker(), tradingPair.getLongTickerEntryPrice(),
                tradingPair.getShortTicker(), tradingPair.getShortTickerEntryPrice(),
                tradingPair.getZScoreEntry());
    }


}
