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

        // ИСПРАВЛЕНИЕ: Добавляем только новые точки в историю Z-Score, избегая дубликатов
        List<ZScoreParam> existingHistory = tradingPair.getZScoreHistory();
        List<ZScoreParam> newHistory = zScoreData.getZScoreHistory();
        
        if (existingHistory.isEmpty()) {
            // Если история пустая, добавляем всю новую историю (для новых пар)
            log.info("📊 История Z-Score пустая для пары {} - добавляем {} новых точек",
                    tradingPair.getPairName(), newHistory.size());
            for (ZScoreParam param : newHistory) {
                tradingPair.addZScorePoint(param);
            }
        } else {
            // Если история есть, добавляем только новые точки
            long lastTimestamp = existingHistory.get(existingHistory.size() - 1).getTimestamp();
            int addedCount = 0;
            
            for (ZScoreParam param : newHistory) {
                if (param.getTimestamp() > lastTimestamp) {
                    tradingPair.addZScorePoint(param);
                    addedCount++;
                }
            }
            
            log.info("📊 Добавлено {} новых точек Z-Score для пары {} (было: {}, стало: {})",
                    addedCount, tradingPair.getPairName(), existingHistory.size(), 
                    tradingPair.getZScoreHistory().size());
        }
    }
}
