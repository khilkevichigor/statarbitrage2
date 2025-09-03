package com.example.core.services;

import com.example.shared.dto.ProfitHistoryItem;
import com.example.shared.models.PortfolioHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для получения исторических данных PnL портфолио
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioPnLHistoryService {

    private final PortfolioHistoryService portfolioHistoryService;

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
     * Получить исторические данные PnL баланса за указанный период
     */
    public List<ProfitHistoryItem> getPnLHistory(PnLPeriod period) {
        try {
            log.debug("🔄 Получение истории PnL реального баланса за период: {}", period.getDisplayName());

            // Определяем временные рамки
            LocalDateTime fromTime = getFromTime(period);
            LocalDateTime toTime = LocalDateTime.now();

            log.debug("📅 Период: {} - {}", fromTime, toTime);

            // Получаем историю реального баланса из базы данных
            List<PortfolioHistory> balanceHistory = portfolioHistoryService.getPortfolioHistory(fromTime, toTime);

            if (balanceHistory.isEmpty()) {
                log.warn("⚠️ Нет данных истории баланса за период {}", period.getDisplayName());
                return createEmptyHistory(period);
            }

            // Получаем начальный баланс для расчета PnL в процентах
            PortfolioHistory firstRecord = balanceHistory.get(0);
            BigDecimal initialBalance = firstRecord.getTotalBalance();

            if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) == 0) {
                log.warn("⚠️ Начальный баланс равен нулю или null");
                return createEmptyHistory(period);
            }

            // Конвертируем в формат для графика (PnL в процентах от начального баланса)
            List<ProfitHistoryItem> result = new ArrayList<>();

            for (PortfolioHistory record : balanceHistory) {
                BigDecimal currentBalance = record.getTotalBalance();
                if (currentBalance != null) {
                    // Рассчитываем PnL в процентах относительно начального баланса
                    BigDecimal pnlPercent = currentBalance.subtract(initialBalance)
                            .divide(initialBalance, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    result.add(ProfitHistoryItem.builder()
                            .timestamp(record.getTimestampMillis())
                            .profitPercent(pnlPercent.doubleValue())
                            .build());
                }
            }

            log.debug("📊 Получено {} точек данных реального PnL баланса (начальный баланс: {} USDT)",
                    result.size(), initialBalance);
            return result;

        } catch (Exception e) {
            log.error("❌ Ошибка при получении истории PnL реального баланса", e);
            return createEmptyHistory(period);
        }
    }

    /**
     * Определить начальную дату для периода
     */
    private LocalDateTime getFromTime(PnLPeriod period) {
        LocalDateTime now = LocalDateTime.now();

        switch (period) {
            case ONE_DAY:
                return now.minusDays(1);
            case ONE_WEEK:
                return now.minusWeeks(1);
            case ONE_MONTH:
                return now.minusMonths(1);
            case ALL_TIME:
            default:
                return now.minusYears(10); // Достаточно далеко в прошлое
        }
    }


    /**
     * Создать пустую историю для случая отсутствия данных
     */
    private List<ProfitHistoryItem> createEmptyHistory(PnLPeriod period) {
        List<ProfitHistoryItem> emptyHistory = new ArrayList<>();
        LocalDateTime fromTime = getFromTime(period);
        LocalDateTime toTime = LocalDateTime.now();

        long fromTimestamp = fromTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long toTimestamp = toTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

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

        public ProfitHistoryItem getMin() {
            return min;
        }

        public ProfitHistoryItem getMax() {
            return max;
        }
    }
}