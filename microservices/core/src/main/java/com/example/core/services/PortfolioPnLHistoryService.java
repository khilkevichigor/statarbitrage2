package com.example.core.services;

import com.example.core.repositories.TradeHistoryRepository;
import com.example.shared.dto.ProfitHistoryItem;
import com.example.shared.models.TradeHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Сервис для получения исторических данных PnL портфолио
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioPnLHistoryService {

    private final TradeHistoryRepository tradeHistoryRepository;

    /**
     * Период для отображения графика PnL
     */
    public enum PnLPeriod {
        ALL_TIME("За все время"),
        ONE_MONTH("За месяц"),
        ONE_WEEK("За 7 дней"),
        ONE_DAY("За день");

        private final String displayName;

        PnLPeriod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Получить исторические данные PnL за указанный период
     */
    public List<ProfitHistoryItem> getPnLHistory(PnLPeriod period) {
        try {
            log.debug("🔄 Получение PnL истории за период: {}", period.getDisplayName());
            
            // Определяем временные рамки
            long fromTimestamp = getFromTimestamp(period);
            long toTimestamp = System.currentTimeMillis();
            
            log.debug("📅 Период: {} - {}", 
                Instant.ofEpochMilli(fromTimestamp).atZone(ZoneId.systemDefault()),
                Instant.ofEpochMilli(toTimestamp).atZone(ZoneId.systemDefault()));

            // Получаем все записи трейдов за период
            List<TradeHistory> allTrades = tradeHistoryRepository.findAll();
            
            // Фильтруем по периоду и агрегируем данные
            TreeMap<Long, BigDecimal> aggregatedPnL = new TreeMap<>();
            
            for (TradeHistory trade : allTrades) {
                if (trade.getTimestamp() >= fromTimestamp && trade.getTimestamp() <= toTimestamp) {
                    // Группируем по временным интервалам для сглаживания графика
                    long groupedTimestamp = groupTimestamp(trade.getTimestamp(), period);
                    
                    BigDecimal currentProfit = trade.getCurrentProfitPercent();
                    if (currentProfit != null) {
                        aggregatedPnL.merge(groupedTimestamp, currentProfit, BigDecimal::add);
                    }
                }
            }

            // Конвертируем в формат для графика
            List<ProfitHistoryItem> result = new ArrayList<>();
            BigDecimal cumulativePnL = BigDecimal.ZERO;

            for (var entry : aggregatedPnL.entrySet()) {
                cumulativePnL = cumulativePnL.add(entry.getValue());
                result.add(ProfitHistoryItem.builder()
                    .timestamp(entry.getKey())
                    .profitPercent(cumulativePnL.doubleValue())
                    .build());
            }

            // Если нет данных, добавляем нулевые точки
            if (result.isEmpty()) {
                result.add(ProfitHistoryItem.builder()
                    .timestamp(fromTimestamp)
                    .profitPercent(0.0)
                    .build());
                result.add(ProfitHistoryItem.builder()
                    .timestamp(toTimestamp)
                    .profitPercent(0.0)
                    .build());
            }

            log.debug("📊 Получено {} точек данных PnL", result.size());
            return result;
            
        } catch (Exception e) {
            log.error("❌ Ошибка при получении PnL истории", e);
            return createEmptyHistory(period);
        }
    }

    /**
     * Определить начальную временную метку для периода
     */
    private long getFromTimestamp(PnLPeriod period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from;

        switch (period) {
            case ONE_DAY:
                from = now.minusDays(1);
                break;
            case ONE_WEEK:
                from = now.minusWeeks(1);
                break;
            case ONE_MONTH:
                from = now.minusMonths(1);
                break;
            case ALL_TIME:
            default:
                from = now.minusYears(10); // Достаточно далеко в прошлое
                break;
        }

        return from.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Группировка временных меток для сглаживания графика
     */
    private long groupTimestamp(long timestamp, PnLPeriod period) {
        LocalDateTime dateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

        LocalDateTime grouped;
        switch (period) {
            case ONE_DAY:
                // Группируем по часам
                grouped = dateTime.truncatedTo(ChronoUnit.HOURS);
                break;
            case ONE_WEEK:
                // Группируем по 4 часа
                int hour = dateTime.getHour();
                int groupedHour = (hour / 4) * 4;
                grouped = dateTime.withHour(groupedHour).truncatedTo(ChronoUnit.HOURS);
                break;
            case ONE_MONTH:
                // Группируем по дням
                grouped = dateTime.truncatedTo(ChronoUnit.DAYS);
                break;
            case ALL_TIME:
            default:
                // Группируем по неделям
                grouped = dateTime.truncatedTo(ChronoUnit.DAYS)
                    .with(java.time.DayOfWeek.MONDAY);
                break;
        }

        return grouped.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Создать пустую историю для случая отсутствия данных
     */
    private List<ProfitHistoryItem> createEmptyHistory(PnLPeriod period) {
        List<ProfitHistoryItem> emptyHistory = new ArrayList<>();
        long fromTimestamp = getFromTimestamp(period);
        long toTimestamp = System.currentTimeMillis();

        emptyHistory.add(ProfitHistoryItem.builder()
            .timestamp(fromTimestamp)
            .profitPercent(0.0)
            .build());
        
        emptyHistory.add(ProfitHistoryItem.builder()
            .timestamp(toTimestamp)
            .profitPercent(0.0)
            .build());

        return emptyHistory;
    }

    /**
     * Проверить, показывает ли график прибыль (зеленый) или убыток (красный)
     */
    public boolean isProfitable(List<ProfitHistoryItem> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }

        // Сравниваем последнюю точку с первой
        double startValue = history.get(0).getProfitPercent();
        double endValue = history.get(history.size() - 1).getProfitPercent();
        
        return endValue >= startValue;
    }

    /**
     * Получить экстремальные значения для подписей на графике
     */
    public PnLExtremes getExtremes(List<ProfitHistoryItem> history) {
        if (history == null || history.isEmpty()) {
            return new PnLExtremes(null, null);
        }

        ProfitHistoryItem minItem = history.get(0);
        ProfitHistoryItem maxItem = history.get(0);

        for (ProfitHistoryItem item : history) {
            if (item.getProfitPercent() < minItem.getProfitPercent()) {
                minItem = item;
            }
            if (item.getProfitPercent() > maxItem.getProfitPercent()) {
                maxItem = item;
            }
        }

        return new PnLExtremes(minItem, maxItem);
    }

    /**
     * Класс для хранения экстремальных значений
     */
    public static class PnLExtremes {
        private final ProfitHistoryItem min;
        private final ProfitHistoryItem max;

        public PnLExtremes(ProfitHistoryItem min, ProfitHistoryItem max) {
            this.min = min;
            this.max = max;
        }

        public ProfitHistoryItem getMin() { return min; }
        public ProfitHistoryItem getMax() { return max; }
    }
}