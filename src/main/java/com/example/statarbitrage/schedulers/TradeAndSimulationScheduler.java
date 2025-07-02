package com.example.statarbitrage.schedulers;

import com.example.statarbitrage.events.UpdateUiEvent;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.processors.FetchPairsProcessor;
import com.example.statarbitrage.processors.StartNewTradeProcessor;
import com.example.statarbitrage.processors.UpdateTradeProcessor;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.services.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeAndSimulationScheduler {

    private final SettingsService settingsService;
    private final PairDataService pairDataService;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final FetchPairsProcessor fetchPairsProcessor;
    private final EventSendService eventSendService;

    @Scheduled(fixedRate = 60_000)
    public void updateTrades() {
        try {
            // ВСЕГДА обновляем трейды
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (!tradingPairs.isEmpty()) {
                long schedulerStart = System.currentTimeMillis();
                log.info("🔄 Update Trades Scheduler started...");
                long updateTradeStart = System.currentTimeMillis();
                tradingPairs.forEach(updateTradeProcessor::updateTrade);
                long updateTradeEnd = System.currentTimeMillis();
                log.info("⏱️ Update trading pairs finished in {} сек", (updateTradeEnd - updateTradeStart) / 1000.0);
                log.info("✅ Обновлены {} трейдов", tradingPairs.size());
                // Обновляем UI
                eventSendService.updateUI(UpdateUiEvent.builder().build());
                long schedulerEnd = System.currentTimeMillis();
                log.info("⏱️ Update Trades Scheduler finished in {} сек", (schedulerEnd - schedulerStart) / 1000.0);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка в updateTrades()", e);
        }
    }

    @Scheduled(fixedRate = 180_000)
    public void maintainPairs() {
        long schedulerStart = System.currentTimeMillis();
        log.info("🔄 Maintain Pairs Scheduler started...");
        try {
            // ЕСЛИ симуляция включена — поддерживаем нужное количество трейдов
            Settings settings = settingsService.getSettingsFromDb();
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (settings.isSimulationEnabled()) {
                int maxActive = (int) settings.getUsePairs();
                int currentActive = tradingPairs.size();
                int missing = maxActive - currentActive;

                if (missing > 0) {
                    log.info("🆕 Не хватает {} пар (из {}) — начинаем подбор", missing, maxActive);

                    // Удаляем старые SELECTED
                    pairDataService.deleteAllByStatus(TradeStatus.SELECTED);

                    // Находим новые и сразу запускаем
                    log.info("Fetching pairs...");
                    long fetchPairsStart = System.currentTimeMillis();
                    List<PairData> newPairs = fetchPairsProcessor.fetchPairs(missing);
                    long fetchPairsStartEnd = System.currentTimeMillis();
                    log.info("⏱️ Fetching pairs finished in {} сек", (fetchPairsStartEnd - fetchPairsStart) / 1000.0);

                    log.info("Trading new pairs...");
                    long testTradeStart = System.currentTimeMillis();
                    AtomicInteger count = new AtomicInteger();
                    newPairs.forEach((v) -> {
                        PairData startedNewTrade = startNewTradeProcessor.startNewTrade(v);
                        if (startedNewTrade != null) {
                            count.getAndIncrement();
                        }
                    });
                    long testTradeEnd = System.currentTimeMillis();
                    log.info("⏱️ Trading new pairs finished in {} сек", (testTradeEnd - testTradeStart) / 1000.0);

                    log.info("▶️ Запущено {} новых пар", count);
                }
            }

            // Обновляем UI
            eventSendService.updateUI(UpdateUiEvent.builder().build());

        } catch (Exception e) {
            log.error("❌ Ошибка в maintainPairs()", e);
        }

        long schedulerEnd = System.currentTimeMillis();
        log.info("⏱️ Maintain Pairs Scheduler finished in {} сек", (schedulerEnd - schedulerStart) / 1000.0);
    }
}
