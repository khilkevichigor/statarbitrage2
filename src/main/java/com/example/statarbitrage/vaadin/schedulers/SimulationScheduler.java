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
public class SimulationScheduler {
    private final SettingsService settingsService;
    private final PairDataService pairDataService;
    private final TestTradeProcessor testTradeProcessor;
    private final FetchPairsProcessor fetchPairsProcessor;
    private final EventSendService eventSendService;

    @Scheduled(fixedRate = 1 * 60 * 1_000)
    public void runSimulationStep() {
        Settings settings = settingsService.getSettingsFromDb();
        if (!settings.isSimulationEnabled()) {
            return;
        }
        try {
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (tradingPairs.isEmpty()) {
                initializeSimulation(settings);
            } else {
                simulationStep(settings);
            }
        } catch (Exception e) {
            log.error("Ошибка в шаге симуляции", e);
        }
    }

    private void initializeSimulation(Settings settings) {
        // Очищаем старые SELECTED пары
        pairDataService.deleteAllByStatus(TradeStatus.SELECTED);
        eventSendService.updateUI(UpdateUiEvent.builder().build());

        // Загружаем начальный набор пар
        List<PairData> initialPairs = fetchPairsProcessor.fetchPairs();

        // Запускаем первые MAX_ACTIVE_PAIRS пар
        initialPairs.stream()
                .limit((int) settings.getUsePairs())
                .forEach(testTradeProcessor::testTrade);
        eventSendService.updateUI(UpdateUiEvent.builder().build());
    }

    private void simulationStep(Settings settings) {
        try {
            // 1. Получаем текущие торгуемые пары
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);

            // 2. Обновляем данные и проверяем условия выхода
            tradingPairs.forEach(testTradeProcessor::testTrade);
            eventSendService.updateUI(UpdateUiEvent.builder().build());

            // 3. Добираем новые пары до MAX_ACTIVE_PAIRS
            int usePairs = (int) settings.getUsePairs();
            if (tradingPairs.size() < usePairs) {
                int neededPairs = usePairs - tradingPairs.size();
                fetchAndStartNewPairs(neededPairs);
                eventSendService.updateUI(UpdateUiEvent.builder().build());
            }
        } catch (Exception e) {
            log.error("Ошибка в шаге симуляции", e);
        }
    }

    private void fetchAndStartNewPairs(int count) {
        // Получаем новые пары
        List<PairData> newPairs = fetchPairsProcessor.fetchPairs();

        // Запускаем нужное количество
        newPairs.stream()
                .limit(count)
                .forEach(testTradeProcessor::testTrade);
    }
}