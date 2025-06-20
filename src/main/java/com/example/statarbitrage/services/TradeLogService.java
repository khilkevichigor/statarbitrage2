package com.example.statarbitrage.services;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.TradeLog;
import com.example.statarbitrage.repositories.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.example.statarbitrage.constant.Constants.DATE_TIME_FORMAT;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeLogService {
    private final SettingsService settingsService;

    private final TradeLogRepository tradeLogRepository;

    public TradeLog saveFromPairData(PairData pairData) {
        Settings settings = settingsService.getSettings();
        TradeLog tradeLog = new TradeLog();
        tradeLog.setLongTicker(pairData.getLongTicker());
        tradeLog.setShortTicker(pairData.getShortTicker());
        tradeLog.setCurrentProfitPercent(pairData.getProfitChanges());
        tradeLog.setMinProfitPercent(pairData.getMinProfitRounded());
        tradeLog.setMinProfitMinutes(pairData.getTimeInMinutesSinceEntryToMin() + "min");
        tradeLog.setMaxProfitPercent(pairData.getMaxProfitRounded());
        tradeLog.setMaxProfitMinutes(pairData.getTimeInMinutesSinceEntryToMax() + "min");

        tradeLog.setCurrentLongPercent(pairData.getLongChanges());
        tradeLog.setMinLongPercent(pairData.getMinLong());
        tradeLog.setMaxLongPercent(pairData.getMaxLong());

        tradeLog.setCurrentShortPercent(pairData.getShortChanges());
        tradeLog.setMinShortPercent(pairData.getMinShort());
        tradeLog.setMaxShortPercent(pairData.getMaxShort());

        tradeLog.setCurrentZ(pairData.getZScoreCurrent());
        tradeLog.setMinZ(pairData.getMinZ());
        tradeLog.setMaxZ(pairData.getMaxZ());

        tradeLog.setCurrentCorr(pairData.getCorrelationCurrent());
        tradeLog.setMinCorr(pairData.getMinCorr());
        tradeLog.setMaxCorr(pairData.getMaxCorr());

        tradeLog.setExitStop(settings.getExitStop());
        tradeLog.setExitTake(settings.getExitTake());
        tradeLog.setExitZMin(settings.getExitZMin());
        tradeLog.setExitZMax(settings.getExitZMax());
        tradeLog.setExitTimeHours(settings.getExitTimeHours());

        tradeLog.setExitReason(pairData.getExitReason());
        tradeLog.setEntryTime(
                Instant.ofEpochMilli(pairData.getEntryTime()).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT))
        );
        tradeLog.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)));

        tradeLogRepository.save(tradeLog);
        return tradeLog;
    }
}
