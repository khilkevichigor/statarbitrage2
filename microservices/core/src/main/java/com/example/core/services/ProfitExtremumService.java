package com.example.core.services;

import com.example.shared.dto.ChangesData;
import com.example.shared.dto.ProfitExtremum;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitExtremumService {
    private static final long MILLISECONDS_IN_MINUTE = 1000 * 60;

    // Экстремумы берём из pairData, т.к. changesData создаётся заново при каждом вызове
    public ProfitExtremum getProfitExtremums(TradingPair tradingPair, ChangesData changesData) {
        long currentTimeInMinutes = (System.currentTimeMillis() - tradingPair.getEntryTime()) / MILLISECONDS_IN_MINUTE;

        BigDecimal currentProfitPercent = changesData.getProfitPercentChanges() != null
                ? changesData.getProfitPercentChanges()
                : BigDecimal.ZERO;

        // Инициализируем max/min значениями из pairData или currentProfitPercent при первом заходе
        BigDecimal maxProfit = tradingPair.getMaxProfitPercentChanges() != null
                ? tradingPair.getMaxProfitPercentChanges()
                : currentProfitPercent;

        BigDecimal minProfit = tradingPair.getMinProfitPercentChanges() != null
                ? tradingPair.getMinProfitPercentChanges()
                : currentProfitPercent;

        long timeToMax = tradingPair.getMinutesToMaxProfitPercent() > 0
                ? tradingPair.getMinutesToMaxProfitPercent()
                : currentTimeInMinutes;

        long timeToMin = tradingPair.getMinutesToMinProfitPercent() > 0
                ? tradingPair.getMinutesToMinProfitPercent()
                : currentTimeInMinutes;

        // Обновление значений
        if (currentProfitPercent.compareTo(maxProfit) > 0) {
            maxProfit = currentProfitPercent;
            timeToMax = currentTimeInMinutes;
        }

        if (currentProfitPercent.compareTo(minProfit) < 0) {
            minProfit = currentProfitPercent;
            timeToMin = currentTimeInMinutes;
        }

        return new ProfitExtremum(maxProfit, minProfit, timeToMax, timeToMin, currentProfitPercent);
    }
}
