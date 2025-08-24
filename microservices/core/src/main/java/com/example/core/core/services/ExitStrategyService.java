package com.example.core.core.services;

import com.example.shared.models.PairData;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExitStrategyService {

    /**
     * Определяет причину выхода из сделки на основе текущих параметров и настроек.
     *
     * @return Название причины выхода или {@code null}, если оснований для выхода нет.
     */
    public String getExitReason(PairData pairData, Settings settings) {
        double profit = pairData.getProfitPercentChanges().doubleValue();
        double zScoreCurrent = pairData.getZScoreCurrent();
        double zScoreEntry = pairData.getZScoreEntry();
        long entryTimeMillis = pairData.getEntryTime();
        long nowMillis = System.currentTimeMillis();

        if (isStopTriggered(profit, settings)) {
            log.info("Выход по стопу: profit = {}%", profit);
            return ExitReasonType.EXIT_REASON_BY_STOP.name();
        }

        if (isTakeTriggered(profit, settings)) {
            log.info("Выход по тейку: profit = {}%", profit);
            return ExitReasonType.EXIT_REASON_BY_TAKE.name();
        }

        if (isZMinTriggered(zScoreCurrent, settings)) {
            log.info("Выход по Z-Min: zScoreCurrent = {}", zScoreCurrent);
            return ExitReasonType.EXIT_REASON_BY_Z_MIN.name();
        }

        if (isZMaxTriggered(zScoreCurrent, zScoreEntry, settings)) {
            log.info("Выход по Z-Max: zScoreCurrent = {}, zScoreEntry = {}, threshold = {}%",
                    zScoreCurrent, zScoreEntry, settings.getExitZMaxPercent());
            return ExitReasonType.EXIT_REASON_BY_Z_MAX.name();
        }

        if (isTimeExceeded(entryTimeMillis, nowMillis, settings)) {
            long hoursHeld = (nowMillis - entryTimeMillis) / (1000 * 60 * 60);
            log.info("Выход по времени: прошло {} часов", hoursHeld);
            return ExitReasonType.EXIT_REASON_BY_TIME.name();
        }

        if (isCloseAtBreakevenTriggered(pairData, settings)) {
            log.info("Выход по безубытку: profit = {}%", profit);
            return ExitReasonType.EXIT_REASON_BY_BREAKEVEN.name();
        }

        if (isNegativeZMinProfitTriggered(zScoreCurrent, profit, settings)) {
            log.info("Выход по отрицательному Z-Score с минимальным профитом: zScoreCurrent = {}, profit = {}%, minProfit = {}%",
                    zScoreCurrent, profit, settings.getExitNegativeZMinProfitPercent());
            return ExitReasonType.EXIT_REASON_BY_NEGATIVE_Z_MIN_PROFIT.name();
        }

        return null;
    }

    private boolean isStopTriggered(double profit, Settings settings) {
        return settings.isUseExitStop() && profit <= settings.getExitStop();
    }

    private boolean isTakeTriggered(double profit, Settings settings) {
        return settings.isUseExitTake() && profit >= settings.getExitTake();
    }

    private boolean isZMinTriggered(double zScoreCurrent, Settings settings) {
        return settings.isUseExitZMin() && zScoreCurrent <= settings.getExitZMin();
    }

    private boolean isZMaxTriggered(double zCurrent, double zEntry, Settings settings) {
        if (settings.isUseExitZMax() && zCurrent >= zEntry + settings.getExitZMax()) {
            return true;
        }
        return settings.isUseExitZMaxPercent()
                && zCurrent >= zEntry * (1 + settings.getExitZMaxPercent() / 100.0);
    }

    private boolean isTimeExceeded(long entryTimeMillis, long nowMillis, Settings settings) {
        if (!settings.isUseExitTimeMinutes() || entryTimeMillis <= 0) {
            return false;
        }
        long minutesHeld = (nowMillis - entryTimeMillis) / (1000 * 60); // перевод в минуты
        return minutesHeld >= settings.getExitTimeMinutes(); // сравнение с минутами
    }

    private boolean isCloseAtBreakevenTriggered(PairData pairData, Settings settings) {
        if (!settings.isUseExitBreakEvenPercent()) {
            return false;
        }
        return pairData.isCloseAtBreakeven() && pairData.getProfitPercentChanges().doubleValue() >= settings.getExitBreakEvenPercent(); //1% чтобы гарантировать БУ
    }

    /**
     * Проверяет условие выхода при отрицательном Z-Score с минимальным профитом
     *
     * @param zScoreCurrent текущий Z-Score
     * @param profit        текущий профит в процентах
     * @param settings      настройки торговли
     * @return true если нужно закрывать позицию
     */
    private boolean isNegativeZMinProfitTriggered(double zScoreCurrent, double profit, Settings settings) {
        if (!settings.isUseExitNegativeZMinProfitPercent()) {
            return false;
        }

        // Z-Score стал отрицательным (направление торговли изменилось)
        // И у нас есть минимальный требуемый профит
        return zScoreCurrent < 0.0 && profit >= settings.getExitNegativeZMinProfitPercent();
    }
}
