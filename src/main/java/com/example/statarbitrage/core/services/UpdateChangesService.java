package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.ChangesData;
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

        changes.setMinZ(changes.getMinZ());
        pairData.setMaxZ(changes.getMaxZ());

        pairData.setMinCorr(changes.getMinCorr());
        pairData.setMaxCorr(changes.getMaxCorr());

        pairData.setMinProfitChanges(changes.getMinProfitChanges());
        pairData.setMaxProfitChanges(changes.getMaxProfitChanges());
        pairData.setProfitPercentChanges(changes.getProfitPercentChanges());
        pairData.setProfitUSDTChanges(changes.getProfitUSDTChanges());

        pairData.setTimeInMinutesSinceEntryToMin(changes.getTimeInMinutesSinceEntryToMin());
        pairData.setTimeInMinutesSinceEntryToMax(changes.getTimeInMinutesSinceEntryToMax());

        pairData.setZScoreChanges(changes.getZScoreChanges());
    }
}
