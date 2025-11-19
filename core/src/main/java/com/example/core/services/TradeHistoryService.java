package com.example.core.services;

import com.example.core.repositories.TradeHistoryRepository;
import com.example.shared.models.Settings;
import com.example.shared.models.TradeHistory;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeHistoryService {
    private final TradeHistoryRepository tradeHistoryRepository;

    public void updateTradeLog(Pair pair, Settings settings) {
        String longTicker = pair.getLongTicker();
        String shortTicker = pair.getShortTicker();
        String pairDataUuid = pair.getUuid().toString();

        // ищем по uuid
        Optional<TradeHistory> optional = tradeHistoryRepository.findLatestByUuid(pairDataUuid);

        TradeHistory tradeHistory = optional.orElseGet(TradeHistory::new);

        tradeHistory.setLongTicker(longTicker);
        tradeHistory.setShortTicker(shortTicker);
        tradeHistory.setPairUuid(pairDataUuid);

        // мапим поля
        tradeHistory.setMinProfitPercent(pair.getMinProfitPercentChanges());
        tradeHistory.setMinProfitMinutes(pair.getMinutesToMinProfitPercent() + "min");

        tradeHistory.setMaxProfitPercent(pair.getMaxProfitPercentChanges());
        tradeHistory.setMaxProfitMinutes(pair.getMinutesToMaxProfitPercent() + "min");

        tradeHistory.setCurrentProfitUSDT(pair.getProfitUSDTChanges());
        tradeHistory.setCurrentProfitPercent(pair.getProfitPercentChanges());

        tradeHistory.setMinLongPercent(pair.getMinLong());
        tradeHistory.setMaxLongPercent(pair.getMaxLong());
        tradeHistory.setCurrentLongPercent(pair.getLongPercentChanges());

        tradeHistory.setMinShortPercent(pair.getMinShort());
        tradeHistory.setMaxShortPercent(pair.getMaxShort());
        tradeHistory.setCurrentShortPercent(pair.getShortPercentChanges());

        tradeHistory.setMinZ(pair.getMinZ());
        tradeHistory.setMaxZ(pair.getMaxZ());
        tradeHistory.setCurrentZ(pair.getZScoreCurrent() != null ? pair.getZScoreCurrent().doubleValue() : 0.0);

        tradeHistory.setMinCorr(pair.getMinCorr() != null ? pair.getMinCorr() : BigDecimal.ZERO);
        tradeHistory.setMaxCorr(pair.getMaxCorr() != null ? pair.getMaxCorr() : BigDecimal.ZERO);
        tradeHistory.setCurrentCorr(pair.getCorrelationCurrent() != null ? pair.getCorrelationCurrent().doubleValue() : 0.0);

        tradeHistory.setExitTake(settings.getExitTake());
        tradeHistory.setExitStop(settings.getExitStop());
        tradeHistory.setExitZMin(settings.getExitZMin());
        tradeHistory.setExitZMax(settings.getExitZMaxPercent());
        tradeHistory.setExitTimeMinutes(settings.getExitTimeMinutes());

        tradeHistory.setExitReason(pair.getExitReason());

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
        tradeHistory.setEntryTime(pair.getEntryTime() != null ?
                pair.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                System.currentTimeMillis());
//        tradeHistory.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        tradeHistory.setTimestamp(System.currentTimeMillis());

        tradeHistoryRepository.save(tradeHistory);
    }

    public BigDecimal getSumRealizedProfitUSDTToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return tradeHistoryRepository.getSumRealizedProfitUSDTToday(startOfDay);
    }

    public BigDecimal getSumRealizedProfitUSDTTotal() {
        return tradeHistoryRepository.getSumRealizedProfitUSDTTotal();
    }

    public BigDecimal getSumRealizedProfitPercentToday() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return tradeHistoryRepository.getSumRealizedProfitPercentToday(startOfDay);
    }

    public BigDecimal getSumRealizedProfitPercentTotal() {
        return tradeHistoryRepository.getSumRealizedProfitPercentTotal();
    }

    public List<TradeHistory> getAll() {
        return tradeHistoryRepository.findAll();
    }
}
