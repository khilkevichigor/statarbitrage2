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
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import com.example.statarbitrage.trading.services.TradingProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeAndSimulationScheduler {

    // Флаги для синхронизации шедуллеров
    private final AtomicBoolean updateTradesRunning = new AtomicBoolean(false);
    private final AtomicBoolean maintainPairsRunning = new AtomicBoolean(false);

    private final SettingsService settingsService;
    private final PairDataService pairDataService;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final FetchPairsProcessor fetchPairsProcessor;
    private final EventSendService eventSendService;
    private final TradingIntegrationService tradingIntegrationService;
    private final TradingProviderFactory tradingProviderFactory;

    @Scheduled(cron = "0 * * * * *") // Каждую минуту в 0 секунд
    public void updateTrades() {
        // Проверяем, не выполняется ли поддержание пар
        if (maintainPairsRunning.get()) {
            log.info("⏸️ Обновление трейдов пропущено - выполняется поддержание пар");
            return;
        }

        // Устанавливаем флаг выполнения
        if (!updateTradesRunning.compareAndSet(false, true)) {
            log.warn("⚠️ Обновление трейдов уже выполняется");
            return;
        }

        long schedulerStart = System.currentTimeMillis();
        List<PairData> tradingPairs = List.of();

        try {
            log.info("🔄 Шедуллера обновления трейдов запущен...");
            // ВСЕГДА обновляем трейды
            tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (!tradingPairs.isEmpty()) {
                // Обновляем цены позиций в торговой системе
                tradingIntegrationService.updateAllPositions();

                tradingPairs.forEach(p -> {
                    try {
                        updateTradeProcessor.updateTrade(p, false);
                    } catch (Exception e) {
                        log.warn("⚠️ Ошибка при обновлении пары {}/{}: {}",
                                p.getLongTicker(), p.getShortTicker(), e.getMessage());
                        // Продолжаем обработку остальных пар
                    }
                });
                // Обновляем UI
                eventSendService.updateUI(UpdateUiEvent.builder().build());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка в updateTrades()", e);
        } finally {
            // Сбрасываем флаг выполнения
            updateTradesRunning.set(false);
        }

        long schedulerEnd = System.currentTimeMillis();
        log.info("⏱️ Шедуллер обновления трейдов закончил работу за {} сек. Обновлено {} трейдов", (schedulerEnd - schedulerStart) / 1000.0, tradingPairs.size());
    }

    @Scheduled(cron = "0 */5 * * * *") // Каждые 5 минут в 0 секунд
    public void maintainPairs() {
        // Ждем завершения обновления трейдов если оно выполняется
        if (updateTradesRunning.get()) {
            log.info("⏳ Поддержание пар ждет завершения обновления трейдов...");
            // Ждем максимум 60 секунд
            int waitTime = 0;
            while (updateTradesRunning.get() && waitTime < 60_000) {
                try {
                    Thread.sleep(1000);
                    waitTime += 1000;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Прерывание ожидания завершения обновления трейдов");
                    return;
                }
            }
            if (updateTradesRunning.get()) {
                log.warn("⚠️ Поддержание пар отменено - обновление трейдов выполняется слишком долго");
                return;
            }
        }

        // Устанавливаем флаг выполнения
        if (!maintainPairsRunning.compareAndSet(false, true)) {
            log.warn("⚠️ Поддержание пар уже выполняется");
            return;
        }

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
                        try {
                            PairData startedNewTrade = startNewTradeProcessor.startNewTrade(v);
                            if (startedNewTrade != null) {
                                count.getAndIncrement();
                            }
                        } catch (Exception e) {
                            log.warn("⚠️ Не удалось запустить новый трейд для пары {}/{}: {}",
                                    v.getLongTicker(), v.getShortTicker(), e.getMessage());
                            // Продолжаем обработку остальных пар
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
        } finally {
            // Сбрасываем флаг выполнения
            maintainPairsRunning.set(false);
        }

        long schedulerEnd = System.currentTimeMillis();
        log.info("⏱️ Шедуллер поддержания кол-ва трейдов закончил работу за {} сек. Запущено {} новых пар", (schedulerEnd - schedulerStart) / 1000.0, count);
    }
}
