package com.example.core.schedulers;

import com.example.core.processors.UpdateTradeProcessor;
import com.example.core.services.EventSendService;
import com.example.core.services.PairService;
import com.example.shared.dto.UpdateTradeRequest;
import com.example.shared.enums.TradeStatus;
import com.example.shared.events.UpdateUiEvent;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateTradesScheduler {

    // Флаги для синхронизации шедуллеров
    private final AtomicBoolean updateTradesRunning = new AtomicBoolean(false);
    private final AtomicBoolean maintainPairsRunning = new AtomicBoolean(false);

    private final PairService pairService;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final EventSendService eventSendService;

    @Scheduled(initialDelay = 15000, fixedRate = 60000) // Каждую минуту в 0 секунд
    public void updateTrades() {
        if (!canStartUpdateTrades()) {
            return;
        }

        long schedulerStart = System.currentTimeMillis();

        try {
            List<Pair> tradingPairs = executeUpdateTrades();
            logUpdateTradesCompletion(schedulerStart, tradingPairs.size());
        } finally {
            updateTradesRunning.set(false);
        }
    }

    //todo считать по балансу сколько можно пар вести! например баланс 10к и делаем 10 пар по 1к (по 500$)! Тогда оставляем 10% на запас на просадку! Итого откроем 9 пар на 9к!
    // И так считать каждый раз! При росте баланса будем открывать больше пар и наоборот! Полный автотрейдинг!=)

    private boolean canStartUpdateTrades() {
        if (maintainPairsRunning.get()) {
            log.debug("⏸️ Обновление трейдов пропущено - выполняется поддержание пар");
            return false;
        }

        if (!updateTradesRunning.compareAndSet(false, true)) {
            log.warn("⚠️ Обновление трейдов уже выполняется");
            return false;
        }

        return true;
    }

    private List<Pair> executeUpdateTrades() {
        log.debug("🔄 Шедуллер обновления трейдов запущен...");

        List<Pair> updatablePairs = getUpdatablePairs();
        if (updatablePairs.isEmpty()) {
            return updatablePairs;
        }

        processTradeUpdates(updatablePairs);
        updateUI();

        return updatablePairs;
    }

    private List<Pair> getUpdatablePairs() {
        try {
            return pairService.findAllByStatusIn(List.of(TradeStatus.TRADING, TradeStatus.OBSERVED));
        } catch (Exception e) {
            log.error("❌ Ошибка при получении торговых пар: {}", e.getMessage());
            return List.of();
        }
    }

    private void processTradeUpdates(List<Pair> updatablePairs) {
        updatablePairs.forEach(this::updateSingleTrade);
    }

    private void updateSingleTrade(Pair tradingPair) {
        try {
            if (tradingPair.getStatus() == TradeStatus.TRADING) {
                updateTradeProcessor.updateTrade(UpdateTradeRequest.builder()
                        .tradingPair(tradingPair)
                        .closeManually(false)
                        .build());
            } else if (tradingPair.getStatus() == TradeStatus.OBSERVED) {
                // Here we will call a new method to only update cointegration data
                updateTradeProcessor.updateObservedPair(tradingPair);
            }
        } catch (Exception e) {
            log.warn("⚠️ Ошибка при обновлении пары {}: {}", tradingPair.getPairName(), e.getMessage());
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
        log.debug("⏱️ Шедуллер обновления трейдов закончил работу за {} сек. Обновлено {} трейдов",
                duration / 1000.0, tradesCount);
    }
}
