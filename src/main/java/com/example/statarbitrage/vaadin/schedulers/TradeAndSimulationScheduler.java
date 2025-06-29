package com.example.statarbitrage.vaadin.schedulers;

import com.example.statarbitrage.events.UpdateUiEvent;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.vaadin.processors.FetchPairsProcessor;
import com.example.statarbitrage.vaadin.processors.TestTradeProcessor;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeAndSimulationScheduler {

    private final SettingsService settingsService;
    private final PairDataService pairDataService;
    private final TestTradeProcessor testTradeProcessor;
    private final FetchPairsProcessor fetchPairsProcessor;
    private final EventSendService eventSendService;

    @Scheduled(fixedRate = 60_000)
    public void updateTradesAndMaintainPairs() {
        long start = System.currentTimeMillis();
        log.info("🔄 Scheduler started...");

        try {
            // 1. ВСЕГДА обновляем трейды
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (!tradingPairs.isEmpty()) {
                tradingPairs.forEach(testTradeProcessor::testTrade);
                log.info("✅ Обновлены {} трейдов", tradingPairs.size());
            }

            // 2. ЕСЛИ симуляция включена — поддерживаем нужное количество трейдов
            Settings settings = settingsService.getSettingsFromDb();
            if (settings.isSimulationEnabled()) {
                int maxActive = (int) settings.getUsePairs();
                int currentActive = tradingPairs.size();
                int missing = maxActive - currentActive;

                if (missing > 0) {
                    log.info("🆕 Не хватает {} пар (из {}) — начинаем подбор", missing, maxActive);

                    // Удаляем старые SELECTED
                    pairDataService.deleteAllByStatus(TradeStatus.SELECTED);

                    // Находим новые и сразу запускаем
                    List<PairData> newPairs = fetchPairsProcessor.fetchPairs(missing);
                    newPairs.forEach(testTradeProcessor::testTrade);

                    log.info("▶️ Запущено {} новых пар", newPairs.size());
                }
            }

            // 3. Обновляем UI
            eventSendService.updateUI(UpdateUiEvent.builder().build());

        } catch (Exception e) {
            log.error("❌ Ошибка в TradeAndSimulationScheduler", e);
        }

        long end = System.currentTimeMillis();
        log.info("⏱️ Scheduler finished in {} сек", (end - start) / 1000.0);
    }
}
