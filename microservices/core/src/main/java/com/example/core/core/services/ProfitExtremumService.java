package com.example.core.core.services;

import com.example.core.common.dto.ChangesData;
import com.example.core.common.dto.ProfitExtremum;
import com.example.core.common.model.PairData;
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

        BigDecimal currentProfitPercent = changesData.getProfitPercentChanges() != null
                ? changesData.getProfitPercentChanges()
                : BigDecimal.ZERO;

        // Инициализируем max/min значениями из pairData или currentProfitPercent при первом заходе
        BigDecimal maxProfit = pairData.getMaxProfitPercentChanges() != null
                ? pairData.getMaxProfitPercentChanges()
                : currentProfitPercent;

        BigDecimal minProfit = pairData.getMinProfitPercentChanges() != null
                ? pairData.getMinProfitPercentChanges()
                : currentProfitPercent;

        long timeToMax = pairData.getMinutesToMaxProfitPercent() > 0
                ? pairData.getMinutesToMaxProfitPercent()
                : currentTimeInMinutes;

        long timeToMin = pairData.getMinutesToMinProfitPercent() > 0
                ? pairData.getMinutesToMinProfitPercent()
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
