package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeHistory;
import com.example.statarbitrage.core.repositories.TradeHistoryRepository;
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
    private final TradeHistoryRepository tradeHistoryRepository;

    public void updateTradeLog(PairData pairData, Settings settings) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();
        String pairDataUuid = pairData.getUuid();

        // ищем по uuid
        Optional<TradeHistory> optional = tradeHistoryRepository.findLatestByUuid(pairDataUuid);

        TradeHistory tradeHistory = optional.orElseGet(TradeHistory::new);

        tradeHistory.setLongTicker(longTicker);
        tradeHistory.setShortTicker(shortTicker);
        tradeHistory.setPairUuid(pairDataUuid);

        // мапим поля
        tradeHistory.setMinProfitPercent(pairData.getMinProfitChanges());
        tradeHistory.setMinProfitMinutes(pairData.getTimeInMinutesSinceEntryToMinProfit() + "min");

        tradeHistory.setMaxProfitPercent(pairData.getMaxProfitChanges());
        tradeHistory.setMaxProfitMinutes(pairData.getTimeInMinutesSinceEntryToMaxProfit() + "min");

        tradeHistory.setCurrentProfitUSDT(pairData.getProfitUSDTChanges());
        tradeHistory.setCurrentProfitPercent(pairData.getProfitPercentChanges());

        tradeHistory.setMinLongPercent(pairData.getMinLong());
        tradeHistory.setMaxLongPercent(pairData.getMaxLong());
        tradeHistory.setCurrentLongPercent(pairData.getLongPercentChanges());

        tradeHistory.setMinShortPercent(pairData.getMinShort());
        tradeHistory.setMaxShortPercent(pairData.getMaxShort());
        tradeHistory.setCurrentShortPercent(pairData.getShortPercentChanges());

        tradeHistory.setMinZ(pairData.getMinZ());
        tradeHistory.setMaxZ(pairData.getMaxZ());
        tradeHistory.setCurrentZ(pairData.getZScoreCurrent());

        tradeHistory.setMinCorr(pairData.getMinCorr());
        tradeHistory.setMaxCorr(pairData.getMaxCorr());
        tradeHistory.setCurrentCorr(pairData.getCorrelationCurrent());

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

        tradeHistoryRepository.save(tradeHistory);
    }

    public BigDecimal getSumRealizedProfitUSDT() {
        return tradeHistoryRepository.getSumRealizedProfitUSDT();
    }

    public BigDecimal getSumRealizedProfitPercent() {
        return tradeHistoryRepository.getSumRealizedProfitPercent();
    }

    public List<TradeHistory> getAll() {
        return tradeHistoryRepository.findAll();
    }
}
