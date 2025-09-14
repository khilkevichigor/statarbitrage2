package com.example.core.services;

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
public class UpdateZScoreDataCurrentService {
    public void updateCurrent(Pair tradingPair, ZScoreData zScoreData) {
        if (zScoreData.getZScoreHistory() == null || zScoreData.getZScoreHistory().isEmpty()) {
            log.error("Z-score history is empty for pair {}", tradingPair.getPairName());
            return;
        }
        ZScoreParam latestParam = zScoreData.getZScoreHistory().get(zScoreData.getZScoreHistory().size() - 1);
        tradingPair.setZScoreCurrent(BigDecimal.valueOf(latestParam.getZscore()));
        tradingPair.setCorrelationCurrent(BigDecimal.valueOf(latestParam.getCorrelation()));
        tradingPair.setAdfPvalueCurrent(BigDecimal.valueOf(latestParam.getAdfpvalue()));
        tradingPair.setPValueCurrent(BigDecimal.valueOf(latestParam.getPvalue()));
        tradingPair.setMeanCurrent(BigDecimal.valueOf(latestParam.getMean()));
        tradingPair.setStdCurrent(BigDecimal.valueOf(latestParam.getStd()));
        tradingPair.setSpreadCurrent(BigDecimal.valueOf(latestParam.getSpread()));
        tradingPair.setAlphaCurrent(BigDecimal.valueOf(latestParam.getAlpha()));
        tradingPair.setBetaCurrent(BigDecimal.valueOf(latestParam.getBeta()));

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
