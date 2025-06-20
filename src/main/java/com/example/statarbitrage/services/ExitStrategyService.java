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
    private final PairDataService pairDataService;
    private final SettingsService settingsService;

    public boolean isExitStrategyAcceptedAndAddReason(PairData pairData) {
        Settings settings = settingsService.getSettings();
        double profit = pairData.getProfitChanges().doubleValue();
        double zScore = pairData.getZScoreCurrent();
        long entryTimeMillis = pairData.getEntryTime();
        long nowMillis = System.currentTimeMillis();

        boolean takeProfitOrStopLoss = false;
        boolean zScoreReturned = false;
        boolean timedOut = false;

        // Проверка прибыли
        if (profit <= settings.getExitStop()) {
            takeProfitOrStopLoss = true;
            log.info("Выход по стопу: profit = {}%", profit);
            pairData.setExitReason(EXIT_REASON_BY_STOP);
            pairDataService.save(pairData);
        } else if (profit >= settings.getExitTake()) {
            takeProfitOrStopLoss = true;
            log.info("Выход по тейку: profit = {}%", profit);
            pairData.setExitReason(EXIT_REASON_BY_TAKE);
            pairDataService.save(pairData);
        }

        // Проверка Z-Score
        if (Math.abs(zScore) < settings.getExitZ()) {
            zScoreReturned = true;
            log.info("Выход по Z-Score: zScore = {}", zScore);
            pairData.setExitReason(EXIT_REASON_BY_Z);
            pairDataService.save(pairData);
        }

        // Проверка по времени
        if (entryTimeMillis > 0) {
            long holdingHours = (nowMillis - entryTimeMillis) / (1000 * 60 * 60);
            if (holdingHours >= settings.getExitTimeHours()) {
                timedOut = true;
                log.info("Выход по времени: ожидали {} часов", holdingHours);
                pairData.setExitReason(EXIT_REASON_BY_TIME);
                pairDataService.save(pairData);
            }
        }

        return takeProfitOrStopLoss || zScoreReturned || timedOut;
    }
}