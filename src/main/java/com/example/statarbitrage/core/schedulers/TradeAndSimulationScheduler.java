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
import com.example.statarbitrage.ui.dto.FetchPairsRequest;
import com.example.statarbitrage.ui.dto.StartNewTradeRequest;
import com.example.statarbitrage.ui.dto.UpdateTradeRequest;
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

    @Scheduled(cron = "0 * * * * *") // Каждую минуту в 0 секунд
    public void updateTrades() {
        if (!canStartUpdateTrades()) {
            return;
        }

        long schedulerStart = System.currentTimeMillis();

        try {
            List<PairData> tradingPairs = executeUpdateTrades();
            logUpdateTradesCompletion(schedulerStart, tradingPairs.size());
        } finally {
            updateTradesRunning.set(false);
        }
    }

    //todo считать по балансу сколько можно пар вести! например баланс 10к и делаем 10 пар по 1к (по 500$)! Тогда оставляем 10% на запас на просадку! Итого откроем 9 пар на 9к!
    // И так считать каждый раз! При росте баланса будем открывать больше пар и наоборот! Полный автотрейдинг!=)
    @Scheduled(cron = "0 */5 * * * *") // Каждые 5 минут в 0 секунд
    public void maintainPairs() {
        if (!canStartMaintainPairs()) {
            return;
        }

        long schedulerStart = System.currentTimeMillis();

        try {
            int newPairsCount = executeMaintainPairs();
            logMaintainPairsCompletion(schedulerStart, newPairsCount);
        } finally {
            maintainPairsRunning.set(false);
        }
    }

    private boolean canStartUpdateTrades() {
        if (maintainPairsRunning.get()) {
            log.info("⏸️ Обновление трейдов пропущено - выполняется поддержание пар");
            return false;
        }

        if (!updateTradesRunning.compareAndSet(false, true)) {
            log.warn("⚠️ Обновление трейдов уже выполняется");
            return false;
        }

        return true;
    }

    private List<PairData> executeUpdateTrades() {
        log.info("🔄 Шедуллер обновления трейдов запущен...");

        List<PairData> tradingPairs = getTradingPairs();
        if (tradingPairs.isEmpty()) {
            return tradingPairs;
        }

        updatePositionsPrices();
        processTradeUpdates(tradingPairs);
        updateUI();

        return tradingPairs;
    }

    private List<PairData> getTradingPairs() {
        try {
            return pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        } catch (Exception e) {
            log.error("❌ Ошибка при получении торговых пар: {}", e.getMessage());
            return List.of();
        }
    }

    private void updatePositionsPrices() {
        try {
            tradingIntegrationService.updateAllPositions();
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении цен позиций: {}", e.getMessage());
        }
    }

    private void processTradeUpdates(List<PairData> tradingPairs) {
        tradingPairs.forEach(this::updateSingleTrade);
    }

    private void updateSingleTrade(PairData pairData) {
        try {
            updateTradeProcessor.updateTrade(UpdateTradeRequest.builder()
                    .pairData(pairData)
                    .closeManually(false)
                    .build());
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при обновлении пары {}: {}", pairData.getPairName(), e.getMessage());
        }
    }

    private void updateUI() {
        try {
            eventSendService.updateUI(UpdateUiEvent.builder().build());
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении UI: {}", e.getMessage());
        }
    }

    private void logUpdateTradesCompletion(long startTime, int tradesCount) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("⏱️ Шедуллер обновления трейдов закончил работу за {} сек. Обновлено {} трейдов",
                duration / 1000.0, tradesCount);
    }

    private boolean canStartMaintainPairs() {
        if (!waitForUpdateTradesCompletion()) {
            return false;
        }

        if (!maintainPairsRunning.compareAndSet(false, true)) {
            log.warn("⚠️ Поддержание пар уже выполняется");
            return false;
        }

        return true;
    }

    private boolean waitForUpdateTradesCompletion() {
        if (!updateTradesRunning.get()) {
            return true;
        }

        log.info("⏳ Поддержание пар ждет завершения обновления трейдов...");

        int waitTime = 0;
        final int maxWaitTime = 60_000; // 60 секунд
        final int sleepInterval = 1000; // 1 секунда

        while (updateTradesRunning.get() && waitTime < maxWaitTime) {
            try {
                Thread.sleep(sleepInterval);
                waitTime += sleepInterval;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ Прерывание ожидания завершения обновления трейдов");
                return false;
            }
        }

        if (updateTradesRunning.get()) {
            log.warn("⚠️ Поддержание пар отменено - обновление трейдов выполняется слишком долго");
            return false;
        }

        return true;
    }

    private int executeMaintainPairs() {
        log.info("🔄 Шедуллер поддержания кол-ва трейдов запущен...");

        Settings settings = loadSettings();
        if (settings == null || !settings.isAutoTradingEnabled()) {
            return 0;
        }

        int missingPairs = calculateMissingPairs(settings);
        if (missingPairs <= 0) {
            return 0;
        }

        return createAndStartNewPairs(missingPairs); //todo может тащить настройки сразу отсюда???
    }

    private Settings loadSettings() {
        try {
            return settingsService.getSettings();
        } catch (Exception e) {
            log.error("❌ Ошибка при загрузке настроек: {}", e.getMessage());
            return null;
        }
    }

    private int calculateMissingPairs(Settings settings) {
        try {
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            int maxActive = (int) settings.getUsePairs();
            int currentActive = tradingPairs.size();
            return maxActive - currentActive;
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете недостающих пар: {}", e.getMessage());
            return 0;
        }
    }

    private int createAndStartNewPairs(int missingPairs) {
        log.info("🆕 Не хватает {} пар — начинаем подбор", missingPairs);

        cleanupOldSelectedPairs();

        List<PairData> newPairs = fetchNewPairs(missingPairs);
        if (newPairs.isEmpty()) {
            log.warn("⚠️ Отобрано 0 пар!");
            return 0;
        }

        int startedCount = startNewTrades(newPairs);

        if (startedCount > 0) {
            updateUI();
        }

        return startedCount;
    }

    private void cleanupOldSelectedPairs() {
        try {
            pairDataService.deleteAllByStatus(TradeStatus.SELECTED);
        } catch (Exception e) {
            log.error("❌ Ошибка при очистке старых пар SELECTED: {}", e.getMessage());
        }
    }

    private List<PairData> fetchNewPairs(int count) {
        try {
            return fetchPairsProcessor.fetchPairs(FetchPairsRequest.builder()
                    .countOfPairs(count)
                    .build());
        } catch (Exception e) {
            log.error("❌ Ошибка при поиске новых пар: {}", e.getMessage());
            return List.of();
        }
    }

    private int startNewTrades(List<PairData> newPairs) {
        AtomicInteger count = new AtomicInteger(0);

        newPairs.forEach(pairData -> {
            if (startSingleNewTrade(pairData)) {
                count.incrementAndGet();
            }
        });

        return count.get();
    }

    private boolean startSingleNewTrade(PairData pairData) {
        try {
            PairData result = startNewTradeProcessor.startNewTrade(StartNewTradeRequest.builder()
                    .pairData(pairData)
                    .checkAutoTrading(true)
                    .build());
            return result != null;
        } catch (Exception e) {
            log.warn("⚠️ Не удалось запустить новый трейд для пары {}: {}", pairData.getPairName(), e.getMessage());
            return false;
        }
    }

    private void logMaintainPairsCompletion(long startTime, int newPairsCount) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("⏱️ Шедуллер поддержания кол-ва трейдов закончил работу за {} сек. Запущено {} новых пар", duration / 1000.0, newPairsCount);
    }
}
