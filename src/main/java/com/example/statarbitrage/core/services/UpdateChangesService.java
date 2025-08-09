package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.dto.ProfitHistoryItem;
import com.example.statarbitrage.common.model.PairData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateChangesService {
    public void update(PairData pairData, ChangesData changes) {
        pairData.setMinLong(changes.getMinLong());
        pairData.setMaxLong(changes.getMaxLong());
        pairData.setLongUSDTChanges(changes.getLongUSDTChanges());
        pairData.setLongPercentChanges(changes.getLongPercentChanges());
        pairData.setLongTickerCurrentPrice(changes.getLongCurrentPrice().doubleValue());

        pairData.setMinShort(changes.getMinShort());
        pairData.setMaxShort(changes.getMaxShort());
        pairData.setShortUSDTChanges(changes.getShortUSDTChanges());
        pairData.setShortPercentChanges(changes.getShortPercentChanges());
        pairData.setShortTickerCurrentPrice(changes.getShortCurrentPrice().doubleValue());

        pairData.setMinZ(changes.getMinZ());
        pairData.setMaxZ(changes.getMaxZ());

        pairData.setMinCorr(changes.getMinCorr());
        pairData.setMaxCorr(changes.getMaxCorr());

        pairData.setMinProfitPercentChanges(changes.getMinProfitChanges());
        pairData.setMaxProfitPercentChanges(changes.getMaxProfitChanges());
        pairData.setProfitUSDTChanges(changes.getProfitUSDTChanges());
        pairData.setProfitPercentChanges(changes.getProfitPercentChanges());

        // Добавляем новую точку в историю профита ПОСЛЕ обновления значения
        if (changes.getProfitPercentChanges() != null) {
            log.info("📊 Добавляем точку профита в историю: {}% на время {}",
                    changes.getProfitPercentChanges(), System.currentTimeMillis());
            pairData.addProfitHistoryPoint(ProfitHistoryItem.builder()
                    .timestamp(System.currentTimeMillis())
                    .profitPercent(changes.getProfitPercentChanges().doubleValue())
                    .build());
        }

        pairData.setMinutesToMinProfitPercent(changes.getTimeInMinutesSinceEntryToMinProfit());
        pairData.setMinutesToMaxProfitPercent(changes.getTimeInMinutesSinceEntryToMaxProfit());

        pairData.setZScoreChanges(changes.getZScoreChanges());
    }
}
