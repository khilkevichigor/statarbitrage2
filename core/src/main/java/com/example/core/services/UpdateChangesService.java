package com.example.core.services;

import com.example.shared.dto.ChangesData;
import com.example.shared.dto.ProfitHistoryItem;
import com.example.shared.models.Pair;

import java.util.List;
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

        // Добавляем новую точку в историю профита ПОСЛЕ обновления значения (аналогично Z-Score)
        if (changes.getProfitPercentChanges() != null) {
            long currentTimestamp = System.currentTimeMillis();
            double currentProfitPercent = changes.getProfitPercentChanges().doubleValue();
            
            // Получаем существующую историю для проверки дубликатов
            List<ProfitHistoryItem> existingHistory = tradingPair.getProfitHistory();
            
            // Проверяем дубликаты по времени (избегаем добавления одинаковых записей)
            boolean shouldAdd = true;
            if (!existingHistory.isEmpty()) {
                ProfitHistoryItem lastItem = existingHistory.get(existingHistory.size() - 1);
                long timeDiff = currentTimestamp - lastItem.getTimestamp();
                
                // Если прошло меньше 30 секунд - обновляем последнюю запись вместо добавления новой
                if (timeDiff < 30000) { // 30 секунд
                    log.info("📊 Обновляем последнюю точку профита (прошло {} сек): {}% -> {}% для пары {}", 
                            timeDiff / 1000, lastItem.getProfitPercent(), currentProfitPercent, tradingPair.getPairName());
                    
                    lastItem.setTimestamp(currentTimestamp);
                    lastItem.setProfitPercent(currentProfitPercent);
                    tradingPair.setProfitHistory(existingHistory); // Пересохраняем для обновления JSON
                    shouldAdd = false;
                }
            }
            
            if (shouldAdd) {
                log.info("📊 Добавляем НОВУЮ точку профита в историю: {}% на время {} для пары {} (было {} точек)",
                        currentProfitPercent, currentTimestamp, tradingPair.getPairName(), existingHistory.size());
                
                tradingPair.addProfitHistoryPoint(ProfitHistoryItem.builder()
                        .timestamp(currentTimestamp)
                        .profitPercent(currentProfitPercent)
                        .build());
                        
                log.info("📊 После добавления стало {} точек профита", tradingPair.getProfitHistory().size());
            }
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
