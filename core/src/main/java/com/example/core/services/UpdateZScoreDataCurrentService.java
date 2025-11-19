package com.example.core.services;

import com.example.shared.dto.ZScoreData;
import com.example.shared.dto.ZScoreParam;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateZScoreDataCurrentService {
    public void updateCurrent(Pair pair, ZScoreData zScoreData) {
        if (zScoreData.getZScoreHistory() == null || zScoreData.getZScoreHistory().isEmpty()) {
            log.error("Z-score history is empty for pair {}", pair.getPairName());
            return;
        }
        ZScoreParam latestParam = zScoreData.getZScoreHistory().get(zScoreData.getZScoreHistory().size() - 1);
        pair.setZScoreCurrent(BigDecimal.valueOf(latestParam.getZscore()));
        pair.setCorrelationCurrent(BigDecimal.valueOf(latestParam.getCorrelation()));
        pair.setAdfPvalueCurrent(BigDecimal.valueOf(latestParam.getAdfpvalue()));
        pair.setPValueCurrent(BigDecimal.valueOf(latestParam.getPvalue()));
        pair.setMeanCurrent(BigDecimal.valueOf(latestParam.getMean()));
        pair.setStdCurrent(BigDecimal.valueOf(latestParam.getStd()));
        pair.setSpreadCurrent(BigDecimal.valueOf(latestParam.getSpread()));
        pair.setAlphaCurrent(BigDecimal.valueOf(latestParam.getAlpha()));
        pair.setBetaCurrent(BigDecimal.valueOf(latestParam.getBeta()));

        // ИСПРАВЛЕНИЕ: Добавляем только новые точки в историю Z-Score, избегая дубликатов
        List<ZScoreParam> existingHistory = pair.getZScoreHistory();
        List<ZScoreParam> newHistory = zScoreData.getZScoreHistory();
        
        if (existingHistory.isEmpty()) {
            // Если история пустая, добавляем всю новую историю (для новых пар)
            log.debug("📊 История Z-Score пустая для пары {} - добавляем {} новых точек",
                    pair.getPairName(), newHistory.size());
            for (ZScoreParam param : newHistory) {
                pair.addZScorePoint(param);
            }
        } else {
            // Если история есть, добавляем только новые точки
            long lastTimestamp = existingHistory.get(existingHistory.size() - 1).getTimestamp();
            int addedCount = 0;
            
            for (ZScoreParam param : newHistory) {
                if (param.getTimestamp() > lastTimestamp) {
                    pair.addZScorePoint(param);
                    addedCount++;
                }
            }
            
            log.debug("📊 Добавлено {} новых точек Z-Score для пары {} (было: {}, стало: {})",
                    addedCount, pair.getPairName(), existingHistory.size(),
                    pair.getZScoreHistory().size());
        }
    }
}
