package com.example.core.services;

import com.example.shared.dto.ChangesData;
import com.example.shared.dto.CorrelationHistoryItem;
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
    public void update(Pair pair, ChangesData changes) {
        pair.setMinLong(changes.getMinLong());
        pair.setMaxLong(changes.getMaxLong());
        pair.setLongUSDTChanges(changes.getLongUSDTChanges());
        pair.setLongPercentChanges(changes.getLongPercentChanges());
        pair.setLongTickerCurrentPrice(changes.getLongCurrentPrice());

        pair.setMinShort(changes.getMinShort());
        pair.setMaxShort(changes.getMaxShort());
        pair.setShortUSDTChanges(changes.getShortUSDTChanges());
        pair.setShortPercentChanges(changes.getShortPercentChanges());
        pair.setShortTickerCurrentPrice(changes.getShortCurrentPrice());

        pair.setMinZ(changes.getMinZ());
        pair.setMaxZ(changes.getMaxZ());

        pair.setMinCorr(changes.getMinCorr());
        pair.setMaxCorr(changes.getMaxCorr());

        // Добавляем новую точку в историю корреляции ПОСЛЕ обновления значения (аналогично профиту)
        if (changes.getCorrelationCurrent() != null) {
            long currentTimestamp = System.currentTimeMillis();
            double currentCorrelation = changes.getCorrelationCurrent().doubleValue();
            
            // Получаем существующую историю для проверки дубликатов
            List<CorrelationHistoryItem> existingHistory = pair.getCorrelationHistory();
            
            // Проверяем дубликаты по времени (избегаем добавления одинаковых записей)
            boolean shouldAdd = true;
            if (!existingHistory.isEmpty()) {
                CorrelationHistoryItem lastItem = existingHistory.get(existingHistory.size() - 1);
                long timeDiff = currentTimestamp - lastItem.getTimestamp();
                
                // Если прошло меньше 30 секунд - обновляем последнюю запись вместо добавления новой
                if (timeDiff < 30000) { // 30 секунд
                    log.debug("📊 Обновляем последнюю точку корреляции (прошло {} сек): {} -> {} для пары {}",
                            timeDiff / 1000, lastItem.getCorrelation(), currentCorrelation, pair.getPairName());
                    
                    lastItem.setTimestamp(currentTimestamp);
                    lastItem.setCorrelation(currentCorrelation);
                    pair.setCorrelationHistory(existingHistory); // Пересохраняем для обновления JSON
                    shouldAdd = false;
                }
            }
            
            if (shouldAdd) {
                log.debug("📊 Добавляем НОВУЮ точку корреляции в историю: {} на время {} для пары {} (было {} точек)",
                        currentCorrelation, currentTimestamp, pair.getPairName(), existingHistory.size());
                
                pair.addCorrelationHistoryPoint(CorrelationHistoryItem.builder()
                        .timestamp(currentTimestamp)
                        .correlation(currentCorrelation)
                        .build());
                        
                log.debug("📊 После добавления стало {} точек корреляции", pair.getCorrelationHistory().size());
            }
        }

        pair.setMinProfitPercentChanges(changes.getMinProfitChanges());
        pair.setMaxProfitPercentChanges(changes.getMaxProfitChanges());
        pair.setProfitUSDTChanges(changes.getProfitUSDTChanges());
        pair.setProfitPercentChanges(changes.getProfitPercentChanges());

        // Добавляем новую точку в историю профита ПОСЛЕ обновления значения (аналогично Z-Score)
        if (changes.getProfitPercentChanges() != null) {
            long currentTimestamp = System.currentTimeMillis();
            double currentProfitPercent = changes.getProfitPercentChanges().doubleValue();
            
            // Получаем существующую историю для проверки дубликатов
            List<ProfitHistoryItem> existingHistory = pair.getProfitHistory();
            
            // Проверяем дубликаты по времени (избегаем добавления одинаковых записей)
            boolean shouldAdd = true;
            if (!existingHistory.isEmpty()) {
                ProfitHistoryItem lastItem = existingHistory.get(existingHistory.size() - 1);
                long timeDiff = currentTimestamp - lastItem.getTimestamp();
                
                // Если прошло меньше 30 секунд - обновляем последнюю запись вместо добавления новой
                if (timeDiff < 30000) { // 30 секунд
                    log.debug("📊 Обновляем последнюю точку профита (прошло {} сек): {}% -> {}% для пары {}",
                            timeDiff / 1000, lastItem.getProfitPercent(), currentProfitPercent, pair.getPairName());
                    
                    lastItem.setTimestamp(currentTimestamp);
                    lastItem.setProfitPercent(currentProfitPercent);
                    pair.setProfitHistory(existingHistory); // Пересохраняем для обновления JSON
                    shouldAdd = false;
                }
            }
            
            if (shouldAdd) {
                log.debug("📊 Добавляем НОВУЮ точку профита в историю: {}% на время {} для пары {} (было {} точек)",
                        currentProfitPercent, currentTimestamp, pair.getPairName(), existingHistory.size());
                
                pair.addProfitHistoryPoint(ProfitHistoryItem.builder()
                        .timestamp(currentTimestamp)
                        .profitPercent(currentProfitPercent)
                        .build());
                        
                log.debug("📊 После добавления стало {} точек профита", pair.getProfitHistory().size());
            }
        }

        pair.setMinutesToMinProfitPercent(changes.getTimeInMinutesSinceEntryToMinProfit());
        pair.setMinutesToMaxProfitPercent(changes.getTimeInMinutesSinceEntryToMaxProfit());

        pair.setZScoreChanges(changes.getZScoreChanges());

        // Форматирование значений для UI
        formatProfitValues(pair, changes);
        formatTimeValues(pair, changes);
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
