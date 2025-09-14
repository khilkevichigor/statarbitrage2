package com.example.cointegration.service;

import com.example.shared.dto.ZScoreData;
import com.example.shared.dto.ZScoreParam;
import com.example.shared.models.Pair;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateZScoreDataCurrentService {
    public void updateCurrent(Pair cointPair, ZScoreData zScoreData) {
        if (zScoreData.getZScoreHistory() == null || zScoreData.getZScoreHistory().isEmpty()) {
            log.error("Z-score history is empty for pair {}", cointPair.getPairName());
            return;
        }
        ZScoreParam latestParam = zScoreData.getZScoreHistory().get(zScoreData.getZScoreHistory().size() - 1);
        cointPair.setZScoreCurrent(BigDecimal.valueOf(latestParam.getZscore()));
        cointPair.setCorrelationCurrent(BigDecimal.valueOf(latestParam.getCorrelation()));
        cointPair.setAdfPvalueCurrent(BigDecimal.valueOf(latestParam.getAdfpvalue()));
        cointPair.setPValueCurrent(BigDecimal.valueOf(latestParam.getPvalue()));
        cointPair.setMeanCurrent(BigDecimal.valueOf(latestParam.getMean()));
        cointPair.setStdCurrent(BigDecimal.valueOf(latestParam.getStd()));
        cointPair.setSpreadCurrent(BigDecimal.valueOf(latestParam.getSpread()));
        cointPair.setAlphaCurrent(BigDecimal.valueOf(latestParam.getAlpha()));
        cointPair.setBetaCurrent(BigDecimal.valueOf(latestParam.getBeta()));

        // Добавляем новые точки в историю Z-Score при каждом обновлении
        if (zScoreData.getZScoreHistory() != null && !zScoreData.getZScoreHistory().isEmpty()) {
            // Добавляем всю новую историю из ZScoreData
            for (ZScoreParam param : zScoreData.getZScoreHistory()) {
                cointPair.addZScorePoint(param);
            }
        } else {
            // Если новой истории нет, добавляем хотя бы текущую точку
            cointPair.addZScorePoint(latestParam);
        }

    }
}
