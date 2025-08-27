package com.example.cointegration.schedulers;

import com.example.cointegration.processors.FetchCointPairsProcessor;
import com.example.cointegration.service.CointPairService;
import com.example.cointegration.service.SettingsService;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.models.CointPair;
import com.example.shared.models.Settings;
import com.example.shared.models.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FindCointPairsScheduler {
    private final SettingsService settingsService;
    private final CointPairService cointPairService;
    private final FetchCointPairsProcessor fetchCointPairsProcessor;

    @Scheduled(cron = "0 */1 * * * *")
    public void maintainCointPairs() {
        long schedulerStart = System.currentTimeMillis();
        int newCointPairsCount = executeMaintainCointPairs();
        logMaintainCointPairsCompletion(schedulerStart, newCointPairsCount);
    }

    private int executeMaintainCointPairs() {
        log.info("🔄 Шедуллер поиска коинтегрированных пар запущен...");
        Settings settings = settingsService.getSettings();
        if (settings == null) {
            return 0;
        }
        int missingCointPairs = calculateMissingCointPairs(settings);
        if (missingCointPairs <= 0) {
            return 0;
        }
        return createNewCointPairs(missingCointPairs);
    }

    private int calculateMissingCointPairs(Settings settings) {
        try {
            List<CointPair> tradingPairs = cointPairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            int maxActive = (int) settings.getUsePairs();
            int currentActive = tradingPairs.size();
            return maxActive - currentActive;
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете недостающих пар: {}", e.getMessage());
            return 0;
        }
    }

    private int createNewCointPairs(int missingCointPairs) {
        log.info("🆕 Не хватает {} пар — начинаем подбор", missingCointPairs);
//        cleanupOldCointPairs(); //переместил ближе к созданию ноывых что бы небыло пусто 30сек
        List<CointPair> newCointPairs = fetchNewCointPairs(missingCointPairs);
        if (newCointPairs.isEmpty()) {
            log.warn("⚠️ Отобрано 0 пар!");
            return 0;
        }
        log.info("Отобрано {} пар", newCointPairs.size());
        return newCointPairs.size();
    }

    private void cleanupOldCointPairs() {
        try {
            cointPairService.deleteAllByStatus(TradeStatus.SELECTED);
        } catch (Exception e) {
            log.error("❌ Ошибка при очистке старых пар SELECTED: {}", e.getMessage());
        }
    }

    private List<CointPair> fetchNewCointPairs(int count) {
        try {
            return fetchCointPairsProcessor.fetchCointPairs(FetchPairsRequest.builder()
                    .countOfPairs(count)
                    .build());
        } catch (Exception e) {
            log.error("❌ Ошибка при поиске новых пар: {}", e.getMessage());
            return List.of();
        }
    }

    private void logMaintainCointPairsCompletion(long startTime, int newPairsCount) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("⏱️ Шедуллер поиска коинтегрированных пар закончил работу за {} сек. Найдено {} новых пар", duration / 1000.0, newPairsCount);
    }
}