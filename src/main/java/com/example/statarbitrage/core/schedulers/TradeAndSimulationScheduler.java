package com.example.statarbitrage.core.schedulers;

import com.example.statarbitrage.common.events.UpdateUiEvent;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.processors.FetchPairsProcessor;
import com.example.statarbitrage.core.processors.StartNewTradeProcessor;
import com.example.statarbitrage.core.processors.UpdateTradeProcessor;
import com.example.statarbitrage.core.services.EventSendService;
import com.example.statarbitrage.core.services.PairDataService;
import com.example.statarbitrage.core.services.SettingsService;
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
        long schedulerStart = System.currentTimeMillis();
        log.info("🔄 Шедуллера обновления трейдов запущен...");
        List<PairData> tradingPairs = List.of();
        try {
            // ВСЕГДА обновляем трейды
            tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (!tradingPairs.isEmpty()) {
                tradingPairs.forEach(updateTradeProcessor::updateTrade);
                // Обновляем UI
                eventSendService.updateUI(UpdateUiEvent.builder().build());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка в updateTrades()", e);
        }
        long schedulerEnd = System.currentTimeMillis();
        log.info("⏱️ Шедуллер обновления трейдов закончил работу за {} сек. Обновлено {} трейдов", (schedulerEnd - schedulerStart) / 1000.0, tradingPairs.size());
    }

    @Scheduled(fixedRate = 180_000)
    public void maintainPairs() {
        log.info("🔄 Шедуллер поддержания кол-ва трейдов запущен...");
        long schedulerStart = System.currentTimeMillis();
        AtomicInteger count = new AtomicInteger();
        try {
            // ЕСЛИ автотрейдинг включен — поддерживаем нужное количество трейдов
            Settings settings = settingsService.getSettings();
            if (settings.isAutoTradingEnabled()) {
                List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
                int maxActive = (int) settings.getUsePairs();
                int currentActive = tradingPairs.size();
                int missing = maxActive - currentActive;

                if (missing > 0) {
                    log.info("🆕 Не хватает {} пар (из {}) — начинаем подбор", missing, maxActive);

                    // Удаляем старые SELECTED
                    pairDataService.deleteAllByStatus(TradeStatus.SELECTED);

                    // Находим новые и сразу запускаем
                    List<PairData> newPairs = fetchPairsProcessor.fetchPairs(missing);
                    newPairs.forEach((v) -> {
                        PairData startedNewTrade = startNewTradeProcessor.startNewTrade(v);
                        if (startedNewTrade != null) {
                            count.getAndIncrement();
                        }
                    });
                }
                if (count.get() > 0) {
                    // Обновляем UI
                    eventSendService.updateUI(UpdateUiEvent.builder().build());
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка в maintainPairs()", e);
        }

        long schedulerEnd = System.currentTimeMillis();
        log.info("⏱️ Шедуллер поддержания кол-ва трейдов закончил работу за {} сек. Запущено {} новых пар", (schedulerEnd - schedulerStart) / 1000.0, count);
    }
}
