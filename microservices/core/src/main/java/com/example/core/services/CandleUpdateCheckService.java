package com.example.core.services;

import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Сервис для проверки необходимости пересчета Z-Score
 * на основе таймфрейма и времени последнего обновления
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleUpdateCheckService {

    /**
     * Проверяет нужно ли пересчитывать Z-Score для пары
     * на основе таймфрейма и времени последнего пересчета
     */
    public boolean shouldRecalculateZScore(Pair pair) {
        if (pair == null) {
            log.warn("❌ Пара null - требуется пересчет Z-Score");
            return true;
        }

        LocalDateTime lastZScoreUpdate = pair.getLastZScoreUpdateTime();
        if (lastZScoreUpdate == null) {
            log.info("📊 Первый расчет Z-Score для пары {} - пересчет необходим", pair.getPairName());
            return true;
        }

        String timeframe = pair.getTimeframe();
        if (timeframe == null || timeframe.isEmpty()) {
            log.warn("⚠️ Таймфрейм не указан для пары {} - требуется пересчет", pair.getPairName());
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        Duration timeSinceLastUpdate = Duration.between(lastZScoreUpdate, now);
        Duration timeframeInterval = getTimeframeInterval(timeframe);

        if (timeframeInterval == null) {
            log.warn("⚠️ Неизвестный таймфрейм {} для пары {} - требуется пересчет", 
                    timeframe, pair.getPairName());
            return true;
        }

        boolean shouldRecalculate = timeSinceLastUpdate.compareTo(timeframeInterval) >= 0;

        if (shouldRecalculate) {
            log.info("🔄 Пора пересчитывать Z-Score для пары {} (ТФ: {}, прошло: {} мин, интервал: {} мин)",
                    pair.getPairName(), timeframe, 
                    timeSinceLastUpdate.toMinutes(), timeframeInterval.toMinutes());
        } else {
            log.debug("⏰ Z-Score для пары {} еще актуален (ТФ: {}, прошло: {} мин, интервал: {} мин)",
                    pair.getPairName(), timeframe, 
                    timeSinceLastUpdate.toMinutes(), timeframeInterval.toMinutes());
        }

        return shouldRecalculate;
    }

    /**
     * Возвращает интервал времени для таймфрейма
     */
    private Duration getTimeframeInterval(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "1m" -> Duration.ofMinutes(1);
//            case "3m" -> Duration.ofMinutes(3);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
//            case "30m" -> Duration.ofMinutes(30);
            case "1H" -> Duration.ofHours(1);
//            case "2h" -> Duration.ofHours(2);
            case "4H" -> Duration.ofHours(4);
//            case "6h" -> Duration.ofHours(6);
//            case "8h" -> Duration.ofHours(8);
//            case "12h" -> Duration.ofHours(12);
            case "1D" -> Duration.ofDays(1);
//            case "3d" -> Duration.ofDays(3);
            case "1W" -> Duration.ofDays(7);
            case "1M" -> Duration.ofDays(30); // месяц
            default -> {
                log.error("❌ Неизвестный таймфрейм: {}", timeframe);
                yield null;
            }
        };
    }

    /**
     * Обновляет время последнего пересчета Z-Score для пары
     */
    public void markZScoreUpdated(Pair pair) {
        if (pair != null) {
            pair.setLastZScoreUpdateTime(LocalDateTime.now());
            log.debug("✅ Время последнего пересчета Z-Score обновлено для пары {}", pair.getPairName());
        }
    }

    /**
     * Получает строковое представление времени до следующего обновления
     */
    public String getTimeUntilNextUpdate(Pair pair) {
        if (pair == null || pair.getLastZScoreUpdateTime() == null || pair.getTimeframe() == null) {
            return "Неизвестно";
        }

        LocalDateTime lastUpdate = pair.getLastZScoreUpdateTime();
        Duration timeframeInterval = getTimeframeInterval(pair.getTimeframe());
        
        if (timeframeInterval == null) {
            return "Неизвестно";
        }

        LocalDateTime nextUpdateTime = lastUpdate.plus(timeframeInterval);
        LocalDateTime now = LocalDateTime.now();

        if (now.isAfter(nextUpdateTime)) {
            return "Пора обновлять";
        }

        Duration timeUntilNext = Duration.between(now, nextUpdateTime);
        
        if (timeUntilNext.toDays() > 0) {
            return String.format("%d дн %d ч %d мин", 
                    timeUntilNext.toDays(), 
                    timeUntilNext.toHoursPart(), 
                    timeUntilNext.toMinutesPart());
        } else if (timeUntilNext.toHours() > 0) {
            return String.format("%d ч %d мин", 
                    timeUntilNext.toHours(), 
                    timeUntilNext.toMinutesPart());
        } else {
            return String.format("%d мин", timeUntilNext.toMinutes());
        }
    }

    /**
     * Логирует статистику по обновлениям Z-Score для списка пар
     */
    public void logUpdateStatistics(Iterable<Pair> pairs) {
        int totalPairs = 0;
        int needsUpdate = 0;
        int upToDate = 0;

        for (Pair pair : pairs) {
            totalPairs++;
            if (shouldRecalculateZScore(pair)) {
                needsUpdate++;
            } else {
                upToDate++;
            }
        }

        log.info("📊 Статистика обновлений Z-Score: всего пар: {}, требует обновления: {}, актуальных: {}",
                totalPairs, needsUpdate, upToDate);
    }
}