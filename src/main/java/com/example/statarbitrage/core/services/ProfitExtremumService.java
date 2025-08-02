package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.dto.ProfitExtremum;
import com.example.statarbitrage.common.model.PairData;
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
    public ProfitExtremum getProfitExtremums(PairData pairData, ChangesData changesData) {
        long currentTimeInMinutes = (System.currentTimeMillis() - pairData.getEntryTime()) / MILLISECONDS_IN_MINUTE;

        BigDecimal currentProfit = changesData.getProfitPercentChanges() != null
                ? changesData.getProfitPercentChanges()
                : BigDecimal.ZERO;

        // Инициализируем max/min значениями из pairData или currentProfit при первом заходе
        BigDecimal maxProfit = pairData.getMaxProfitChanges() != null
                ? pairData.getMaxProfitChanges()
                : currentProfit;

        BigDecimal minProfit = pairData.getMinProfitChanges() != null
                ? pairData.getMinProfitChanges()
                : currentProfit;

        long timeToMax = pairData.getTimeInMinutesSinceEntryToMaxProfit() > 0
                ? pairData.getTimeInMinutesSinceEntryToMaxProfit()
                : currentTimeInMinutes;

        long timeToMin = pairData.getTimeInMinutesSinceEntryToMinProfit() > 0
                ? pairData.getTimeInMinutesSinceEntryToMinProfit()
                : currentTimeInMinutes;

        // Обновление значений
        if (currentProfit.compareTo(maxProfit) > 0) {
            maxProfit = currentProfit;
            timeToMax = currentTimeInMinutes;
        }

        if (currentProfit.compareTo(minProfit) < 0) {
            minProfit = currentProfit;
            timeToMin = currentTimeInMinutes;
        }

        return new ProfitExtremum(maxProfit, minProfit, timeToMax, timeToMin, currentProfit);
    }
}
