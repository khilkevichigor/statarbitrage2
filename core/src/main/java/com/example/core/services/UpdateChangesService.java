package com.example.core.services;

import com.example.shared.dto.ChangesData;
import com.example.shared.dto.ProfitHistoryItem;
import com.example.shared.models.Pair;
import com.example.shared.utils.NumberFormatter;
import com.example.shared.utils.TimeFormatterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateChangesService {
    public void update(Pair tradingPair, ChangesData changes) {
        tradingPair.setMinLong(changes.getMinLong());
        tradingPair.setMaxLong(changes.getMaxLong());
        tradingPair.setLongUSDTChanges(changes.getLongUSDTChanges());
        tradingPair.setLongPercentChanges(changes.getLongPercentChanges());
        tradingPair.setLongTickerCurrentPrice(changes.getLongCurrentPrice());

        tradingPair.setMinShort(changes.getMinShort());
        tradingPair.setMaxShort(changes.getMaxShort());
        tradingPair.setShortUSDTChanges(changes.getShortUSDTChanges());
        tradingPair.setShortPercentChanges(changes.getShortPercentChanges());
        tradingPair.setShortTickerCurrentPrice(changes.getShortCurrentPrice());

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
            log.info("📊 Добавляем точку профита в историю: {}% на время {}",
                    changes.getProfitPercentChanges(), System.currentTimeMillis());
            tradingPair.addProfitHistoryPoint(ProfitHistoryItem.builder()
                    .timestamp(System.currentTimeMillis())
                    .profitPercent(changes.getProfitPercentChanges().doubleValue())
                    .build());
        }

        tradingPair.setMinutesToMinProfitPercent(changes.getTimeInMinutesSinceEntryToMinProfit());
        tradingPair.setMinutesToMaxProfitPercent(changes.getTimeInMinutesSinceEntryToMaxProfit());

        tradingPair.setZScoreChanges(changes.getZScoreChanges());

        // Форматирование значений для UI
        formatProfitValues(tradingPair, changes);
        formatTimeValues(tradingPair, changes);
    }

    private void formatProfitValues(Pair tradingPair, ChangesData changes) {
        // Форматирование общего профита в формате "-0.14$/-0.48%"
        if (changes.getProfitUSDTChanges() != null && changes.getProfitPercentChanges() != null) {
            String formattedUSDT = NumberFormatter.format(changes.getProfitUSDTChanges(), 2);
            String formattedPercent = NumberFormatter.format(changes.getProfitPercentChanges(), 2);
            tradingPair.setFormattedProfitCommon(formattedUSDT + "$/" + formattedPercent + "%");
        }

        // Форматирование профита Long позиции
        if (changes.getLongUSDTChanges() != null && changes.getLongPercentChanges() != null) {
            String formattedUSDT = NumberFormatter.format(changes.getLongUSDTChanges(), 2);
            String formattedPercent = NumberFormatter.format(changes.getLongPercentChanges(), 2);
            tradingPair.setFormattedProfitLong(formattedUSDT + "$/" + formattedPercent + "%");
        }

        // Форматирование профита Short позиции
        if (changes.getShortUSDTChanges() != null && changes.getShortPercentChanges() != null) {
            String formattedUSDT = NumberFormatter.format(changes.getShortUSDTChanges(), 2);
            String formattedPercent = NumberFormatter.format(changes.getShortPercentChanges(), 2);
            tradingPair.setFormattedProfitShort(formattedUSDT + "$/" + formattedPercent + "%");
        }
    }

    private void formatTimeValues(Pair tradingPair, ChangesData changes) {
        // Форматирование времени до минимального профита
        if (changes.getTimeInMinutesSinceEntryToMinProfit() > 0) {
            long timeInMillis = changes.getTimeInMinutesSinceEntryToMinProfit() * 60L * 1000L;
            tradingPair.setFormattedTimeToMinProfit(TimeFormatterUtil.formatDurationFromMillis(timeInMillis));
        }

        // Форматирование времени до максимального профита
        if (changes.getTimeInMinutesSinceEntryToMaxProfit() > 0) {
            long timeInMillis = changes.getTimeInMinutesSinceEntryToMaxProfit() * 60L * 1000L;
            tradingPair.setFormattedTimeToMaxProfit(TimeFormatterUtil.formatDurationFromMillis(timeInMillis));
        }
    }
}
