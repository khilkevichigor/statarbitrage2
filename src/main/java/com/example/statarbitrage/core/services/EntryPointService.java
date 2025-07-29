package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.trading.model.TradeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntryPointService {

    public void addEntryPoints(PairData pairData, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        ZScoreParam latestParam = zScoreData.getLastZScoreParam();
        pairData.setLongTickerEntryPrice(openLongTradeResult.getExecutionPrice().doubleValue());
        pairData.setShortTickerEntryPrice(openShortTradeResult.getExecutionPrice().doubleValue());
        pairData.setZScoreEntry(latestParam.getZscore());
        pairData.setCorrelationEntry(latestParam.getCorrelation());
        pairData.setAdfPvalueEntry(latestParam.getAdfpvalue());
        pairData.setPValueEntry(latestParam.getPvalue());
        pairData.setMeanEntry(latestParam.getMean());
        pairData.setStdEntry(latestParam.getStd());
        pairData.setSpreadEntry(latestParam.getSpread());
        pairData.setAlphaEntry(latestParam.getAlpha());
        pairData.setBetaEntry(latestParam.getBeta());
        // Время входа
        pairData.setEntryTime(openLongTradeResult.getExecutionTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000);

        log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z-скор = {}",
                pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
                pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
                pairData.getZScoreEntry());
    }


}
