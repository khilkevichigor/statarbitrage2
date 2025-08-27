package com.example.core.services;

import com.example.core.repositories.TradeHistoryRepository;
import com.example.shared.models.Settings;
import com.example.shared.models.TradeHistory;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeHistoryService {
    private final TradeHistoryRepository tradeHistoryRepository;

    public void updateTradeLog(TradingPair tradingPair, Settings settings) {
        String longTicker = tradingPair.getLongTicker();
        String shortTicker = tradingPair.getShortTicker();
        String pairDataUuid = tradingPair.getUuid().toString();

        // ищем по uuid
        Optional<TradeHistory> optional = tradeHistoryRepository.findLatestByUuid(pairDataUuid);

        TradeHistory tradeHistory = optional.orElseGet(TradeHistory::new);

        tradeHistory.setLongTicker(longTicker);
        tradeHistory.setShortTicker(shortTicker);
        tradeHistory.setPairUuid(pairDataUuid);

        // мапим поля
        tradeHistory.setMinProfitPercent(tradingPair.getMinProfitPercentChanges());
        tradeHistory.setMinProfitMinutes(tradingPair.getMinutesToMinProfitPercent() + "min");

        tradeHistory.setMaxProfitPercent(tradingPair.getMaxProfitPercentChanges());
        tradeHistory.setMaxProfitMinutes(tradingPair.getMinutesToMaxProfitPercent() + "min");

        tradeHistory.setCurrentProfitUSDT(tradingPair.getProfitUSDTChanges());
        tradeHistory.setCurrentProfitPercent(tradingPair.getProfitPercentChanges());

        tradeHistory.setMinLongPercent(tradingPair.getMinLong());
        tradeHistory.setMaxLongPercent(tradingPair.getMaxLong());
        tradeHistory.setCurrentLongPercent(tradingPair.getLongPercentChanges());

        tradeHistory.setMinShortPercent(tradingPair.getMinShort());
        tradeHistory.setMaxShortPercent(tradingPair.getMaxShort());
        tradeHistory.setCurrentShortPercent(tradingPair.getShortPercentChanges());

        tradeHistory.setMinZ(tradingPair.getMinZ());
        tradeHistory.setMaxZ(tradingPair.getMaxZ());
        tradeHistory.setCurrentZ(tradingPair.getZScoreCurrent());

        tradeHistory.setMinCorr(tradingPair.getMinCorr());
        tradeHistory.setMaxCorr(tradingPair.getMaxCorr());
        tradeHistory.setCurrentCorr(tradingPair.getCorrelationCurrent());

        tradeHistory.setExitTake(settings.getExitTake());
        tradeHistory.setExitStop(settings.getExitStop());
        tradeHistory.setExitZMin(settings.getExitZMin());
        tradeHistory.setExitZMax(settings.getExitZMaxPercent());
        tradeHistory.setExitTimeMinutes(settings.getExitTimeMinutes());

        tradeHistory.setExitReason(tradingPair.getExitReason());

//        long entryMillis = tradingPair.getEntryTime(); // long, например 1721511983000
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//
//        // Преобразуем в LocalDateTime через Instant
//        String formattedEntryTime = Instant.ofEpochMilli(entryMillis)
//                .atZone(ZoneId.systemDefault())
//                .toLocalDateTime()
//                .format(formatter);
//
//        tradeHistory.setEntryTime(formattedEntryTime);
        tradeHistory.setEntryTime(tradingPair.getEntryTime());
//        tradeHistory.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        tradeHistory.setTimestamp(System.currentTimeMillis());

        tradeHistoryRepository.save(tradeHistory);
    }

    public BigDecimal getSumRealizedProfitUSDTToday() {
        long startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return tradeHistoryRepository.getSumRealizedProfitUSDTToday(startOfDay);
    }

    public BigDecimal getSumRealizedProfitUSDTTotal() {
        return tradeHistoryRepository.getSumRealizedProfitUSDTTotal();
    }

    public BigDecimal getSumRealizedProfitPercentToday() {
        long startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return tradeHistoryRepository.getSumRealizedProfitPercentToday(startOfDay);
    }

    public BigDecimal getSumRealizedProfitPercentTotal() {
        return tradeHistoryRepository.getSumRealizedProfitPercentTotal();
    }

    public List<TradeHistory> getAll() {
        return tradeHistoryRepository.findAll();
    }
}
