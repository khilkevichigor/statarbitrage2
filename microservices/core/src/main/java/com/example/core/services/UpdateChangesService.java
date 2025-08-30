package com.example.core.services;

import com.example.shared.dto.ChangesData;
import com.example.shared.dto.ProfitHistoryItem;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateChangesService {
    public void update(TradingPair tradingPair, ChangesData changes) {
        tradingPair.setMinLong(changes.getMinLong());
        tradingPair.setMaxLong(changes.getMaxLong());
        tradingPair.setLongUSDTChanges(changes.getLongUSDTChanges());
        tradingPair.setLongPercentChanges(changes.getLongPercentChanges());
        tradingPair.setLongTickerCurrentPrice(changes.getLongCurrentPrice().doubleValue());

        tradingPair.setMinShort(changes.getMinShort());
        tradingPair.setMaxShort(changes.getMaxShort());
        tradingPair.setShortUSDTChanges(changes.getShortUSDTChanges());
        tradingPair.setShortPercentChanges(changes.getShortPercentChanges());
        tradingPair.setShortTickerCurrentPrice(changes.getShortCurrentPrice().doubleValue());

        tradingPair.setMinZ(changes.getMinZ());
        tradingPair.setMaxZ(changes.getMaxZ());

        tradingPair.setMinCorr(changes.getMinCorr());
        tradingPair.setMaxCorr(changes.getMaxCorr());

        tradingPair.setMinProfitPercentChanges(changes.getMinProfitChanges());
        tradingPair.setMaxProfitPercentChanges(changes.getMaxProfitChanges());
        tradingPair.setProfitUSDTChanges(changes.getProfitUSDTChanges());
        tradingPair.setProfitPercentChanges(changes.getProfitPercentChanges());

        // Добавляем новую точку в историю профита ПОСЛЕ обновления значения
        if (changes.getProfitPercentChanges() != null) {
            log.debug("📊 Добавляем точку профита в историю: {}% на время {}",
                    changes.getProfitPercentChanges(), System.currentTimeMillis());
            tradingPair.addProfitHistoryPoint(ProfitHistoryItem.builder()
                    .timestamp(System.currentTimeMillis())
                    .profitPercent(changes.getProfitPercentChanges().doubleValue())
                    .build());
        }

        tradingPair.setMinutesToMinProfitPercent(changes.getTimeInMinutesSinceEntryToMinProfit());
        tradingPair.setMinutesToMaxProfitPercent(changes.getTimeInMinutesSinceEntryToMaxProfit());

        tradingPair.setZScoreChanges(changes.getZScoreChanges());
    }
}
