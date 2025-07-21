package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeHistory;
import com.example.statarbitrage.core.repositories.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeHistoryService {
    private final TradeLogRepository tradeLogRepository;

    public void updateTradeLog(PairData pairData, Settings settings) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();

        // ищем по паре
        Optional<TradeHistory> optional = tradeLogRepository.findLatestByTickers(longTicker, shortTicker);

        TradeHistory tradeHistory = optional.orElseGet(TradeHistory::new);
        tradeHistory.setLongTicker(longTicker);
        tradeHistory.setShortTicker(shortTicker);

        // мапим поля
        tradeHistory.setCurrentProfitPercent(pairData.getProfitChanges());
        tradeHistory.setMinProfitPercent(pairData.getMinProfitChanges());
        tradeHistory.setMinProfitMinutes(pairData.getTimeInMinutesSinceEntryToMin() + "min");
        tradeHistory.setMaxProfitPercent(pairData.getMaxProfitChanges());
        tradeHistory.setMaxProfitMinutes(pairData.getTimeInMinutesSinceEntryToMax() + "min");

        tradeHistory.setCurrentLongPercent(pairData.getLongChanges());
        tradeHistory.setMinLongPercent(pairData.getMinLong());
        tradeHistory.setMaxLongPercent(pairData.getMaxLong());

        tradeHistory.setCurrentShortPercent(pairData.getShortChanges());
        tradeHistory.setMinShortPercent(pairData.getMinShort());
        tradeHistory.setMaxShortPercent(pairData.getMaxShort());

        tradeHistory.setCurrentZ(pairData.getZScoreCurrent());
        tradeHistory.setMinZ(pairData.getMinZ());
        tradeHistory.setMaxZ(pairData.getMaxZ());

        tradeHistory.setCurrentCorr(pairData.getCorrelationCurrent());
        tradeHistory.setMinCorr(pairData.getMinCorr());
        tradeHistory.setMaxCorr(pairData.getMaxCorr());

        tradeHistory.setExitTake(settings.getExitTake());
        tradeHistory.setExitStop(settings.getExitStop());
        tradeHistory.setExitZMin(settings.getExitZMin());
        tradeHistory.setExitZMax(settings.getExitZMaxPercent());
        tradeHistory.setExitTimeHours(settings.getExitTimeHours());

        tradeHistory.setExitReason(pairData.getExitReason());

        long entryMillis = pairData.getEntryTime(); // long, например 1721511983000
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Преобразуем в LocalDateTime через Instant
        String formattedEntryTime = Instant.ofEpochMilli(entryMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(formatter);

        tradeHistory.setEntryTime(formattedEntryTime);
        tradeHistory.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        tradeLogRepository.save(tradeHistory);
    }

    public BigDecimal getSumRealizedProfit() {
        return tradeLogRepository.getSumRealizedProfit();
    }

    public List<TradeHistory> getAll() {
        return tradeLogRepository.findAll();
    }
}
