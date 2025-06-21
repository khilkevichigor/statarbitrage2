package com.example.statarbitrage.services;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.example.statarbitrage.constant.Constants.*;

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
        if (profit <= settings.getExitStop()) {
            log.info("Выход по стопу: profit = {}%", profit);
            return EXIT_REASON_BY_STOP;
        } else if (profit >= settings.getExitTake()) {
            log.info("Выход по тейку: profit = {}%", profit);
            return EXIT_REASON_BY_TAKE;
        }

        // Проверка Z-Score
        if (Math.abs(zScoreCurrent) < settings.getExitZMin()) {
            log.info("Выход по zMin: zMin = {}", zScoreCurrent);
            return EXIT_REASON_BY_Z_MIN;
        } else if (zScoreCurrent >= zScoreEntry * (1 + settings.getExitZMaxPercent() / 100.0)) { //z превысит на х%
            log.info("Выход по zMax: currentZ = {}, entryZ = {}, threshold = {}%", zScoreCurrent, zScoreEntry, settings.getExitZMaxPercent());
            return EXIT_REASON_BY_Z_MAX;
        }

        // Проверка по времени
        if (entryTimeMillis > 0) {
            long holdingHours = (nowMillis - entryTimeMillis) / (1000 * 60 * 60);
            if (holdingHours >= settings.getExitTimeHours()) {
                log.info("Выход по времени: ожидали {} часов", holdingHours);
                return EXIT_REASON_BY_TIME;
            }
        }

        return null;
    }
}