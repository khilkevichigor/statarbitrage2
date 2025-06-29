package com.example.statarbitrage.vaadin.schedulers;

import com.example.statarbitrage.events.UpdateUiEvent;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.vaadin.processors.FetchPairsProcessor;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PairMaintainerScheduler {
    private final SettingsService settingsService;
    private final PairDataService pairDataService;
    private final FetchPairsProcessor fetchPairsProcessor;
    private final EventSendService eventSendService;

    @Scheduled(cron = "35 * * * * *")
    public void maintainActivePairs() {
        Settings settings = settingsService.getSettingsFromDb();
        if (!settings.isSimulationEnabled()) {
            return;
        }

        log.info("🔁 SimulationScheduler: Checking for missing pairs...");

        try {
            int currentActive = pairDataService.countByStatus(TradeStatus.TRADING);
            int maxActive = (int) settings.getUsePairs();
            int missing = maxActive - currentActive;

            if (missing > 0) {
                log.info("🆕 Need to add {} new pairs (current: {}, max: {})", missing, currentActive, maxActive);

                // Удаляем старые SELECTED, если есть
                pairDataService.deleteAllByStatus(TradeStatus.SELECTED);

                // Получаем и сохраняем новые пары
                fetchPairsProcessor.fetchPairs(missing);

                eventSendService.updateUI(UpdateUiEvent.builder().build());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка в SimulationScheduler", e);
        }
    }
}