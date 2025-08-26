package com.example.core.services;

import com.example.shared.dto.ZScoreData;
import com.example.shared.models.TradingPair;
import com.example.shared.models.ZScoreParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateZScoreDataCurrentService {
    public void updateCurrent(TradingPair tradingPair, ZScoreData zScoreData) {
        if (zScoreData.getZScoreHistory() == null || zScoreData.getZScoreHistory().isEmpty()) {
            log.error("Z-score history is empty for pair {}", tradingPair.getPairName());
            return;
        }
        ZScoreParam latestParam = zScoreData.getZScoreHistory().get(zScoreData.getZScoreHistory().size() - 1);
        tradingPair.setZScoreCurrent(latestParam.getZscore());
        tradingPair.setCorrelationCurrent(latestParam.getCorrelation());
        tradingPair.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        tradingPair.setPValueCurrent(latestParam.getPvalue());
        tradingPair.setMeanCurrent(latestParam.getMean());
        tradingPair.setStdCurrent(latestParam.getStd());
        tradingPair.setSpreadCurrent(latestParam.getSpread());
        tradingPair.setAlphaCurrent(latestParam.getAlpha());
        tradingPair.setBetaCurrent(latestParam.getBeta());

        // Добавляем новые точки в историю Z-Score при каждом обновлении
        if (zScoreData.getZScoreHistory() != null && !zScoreData.getZScoreHistory().isEmpty()) {
            // Добавляем всю новую историю из ZScoreData
            for (ZScoreParam param : zScoreData.getZScoreHistory()) {
                tradingPair.addZScorePoint(param);
            }
        } else {
            // Если новой истории нет, добавляем хотя бы текущую точку
            tradingPair.addZScorePoint(latestParam);
        }

    }
}
