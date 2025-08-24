package com.example.core.core.services;

import com.example.shared.dto.ZScoreData;
import com.example.shared.models.PairData;
import com.example.shared.models.ZScoreParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateZScoreDataCurrentService {
    public void updateCurrent(PairData pairData, ZScoreData zScoreData) {
        if (zScoreData.getZScoreHistory() == null || zScoreData.getZScoreHistory().isEmpty()) {
            log.error("Z-score history is empty for pair {}", pairData.getPairName());
            return;
        }
        ZScoreParam latestParam = zScoreData.getZScoreHistory().get(zScoreData.getZScoreHistory().size() - 1);
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        // Добавляем новые точки в историю Z-Score при каждом обновлении
        if (zScoreData.getZScoreHistory() != null && !zScoreData.getZScoreHistory().isEmpty()) {
            // Добавляем всю новую историю из ZScoreData
            for (ZScoreParam param : zScoreData.getZScoreHistory()) {
                pairData.addZScorePoint(param);
            }
        } else {
            // Если новой истории нет, добавляем хотя бы текущую точку
            pairData.addZScorePoint(latestParam);
        }

    }
}
