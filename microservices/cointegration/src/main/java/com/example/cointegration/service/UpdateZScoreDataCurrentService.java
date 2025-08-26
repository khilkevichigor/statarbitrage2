package com.example.cointegration.service;

import com.example.shared.dto.ZScoreData;
import com.example.shared.models.CointPair;
import com.example.shared.models.ZScoreParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateZScoreDataCurrentService {
    public void updateCurrent(CointPair cointPair, ZScoreData zScoreData) {
        if (zScoreData.getZScoreHistory() == null || zScoreData.getZScoreHistory().isEmpty()) {
            log.error("Z-score history is empty for pair {}", cointPair.getPairName());
            return;
        }
        ZScoreParam latestParam = zScoreData.getZScoreHistory().get(zScoreData.getZScoreHistory().size() - 1);
        cointPair.setZScoreCurrent(latestParam.getZscore());
        cointPair.setCorrelationCurrent(latestParam.getCorrelation());
        cointPair.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        cointPair.setPValueCurrent(latestParam.getPvalue());
        cointPair.setMeanCurrent(latestParam.getMean());
        cointPair.setStdCurrent(latestParam.getStd());
        cointPair.setSpreadCurrent(latestParam.getSpread());
        cointPair.setAlphaCurrent(latestParam.getAlpha());
        cointPair.setBetaCurrent(latestParam.getBeta());

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
