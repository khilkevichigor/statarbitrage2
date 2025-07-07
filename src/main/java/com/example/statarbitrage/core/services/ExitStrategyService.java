package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.example.statarbitrage.common.constant.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExitStrategyService {
    private final SettingsService settingsService;

    public String getExitReason(PairData pairData) {
        Settings settings = settingsService.getSettings();
        double profit = pairData.getProfitChanges().doubleValue();
        double zScoreCurrent = pairData.getZScoreCurrent();
        double zScoreEntry = pairData.getZScoreEntry();
        long entryTimeMillis = pairData.getEntryTime();
        long nowMillis = System.currentTimeMillis();

        // Проверка прибыли
        if (settings.isUseExitStop() && profit <= settings.getExitStop()) {
            log.info("Выход по стопу: profit = {}%", profit);
            return EXIT_REASON_BY_STOP;
        }
        if (settings.isUseExitTake() && profit >= settings.getExitTake()) { //todo не сетится - в бд null
            log.info("Выход по тейку: profit = {}%", profit);
            return EXIT_REASON_BY_TAKE;
        }

        // Проверка Z-Score
        if (settings.isUseExitZMin() && zScoreCurrent <= settings.getExitZMin()) {
            log.info("Выход по zMin: zMin = {}", zScoreCurrent);
            return EXIT_REASON_BY_Z_MIN;
        }
        if (settings.isUseExitZMaxPercent() && zScoreCurrent >= zScoreEntry * (1 + settings.getExitZMaxPercent() / 100.0)) { //z превысит на х%
            log.info("Выход по zMax: currentZ = {}, entryZ = {}, threshold = {}%", zScoreCurrent, zScoreEntry, settings.getExitZMaxPercent());
            return EXIT_REASON_BY_Z_MAX;
        }

        // Проверка по времени
        if (settings.isUseExitTimeHours() && entryTimeMillis > 0) {
            long holdingHours = (nowMillis - entryTimeMillis) / (1000 * 60 * 60);
            if (holdingHours >= settings.getExitTimeHours()) {
                log.info("Выход по времени: ожидали {} часов", holdingHours);
                return EXIT_REASON_BY_TIME;
            }
        }

        return null;
    }
}