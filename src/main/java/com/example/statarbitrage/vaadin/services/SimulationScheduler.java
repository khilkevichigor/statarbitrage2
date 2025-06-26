package com.example.statarbitrage.vaadin.services;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.SettingsService;
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

    private static final int MAX_ACTIVE_PAIRS = 10;

    @Scheduled(fixedRate = 1 * 60 * 1_000)
    public void runSimulationStep() {
        if (!settingsService.getSettingsFromDb().isSimulationEnabled()) {
            return;
        }
        try {
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (tradingPairs.isEmpty()) {
                initializeSimulation();
            } else {
                simulationStep();
            }
        } catch (Exception e) {
            log.error("Ошибка в шаге симуляции", e);
        }
    }

    private void initializeSimulation() {
        // Очищаем старые SELECTED пары
        pairDataService.deleteAllByStatus(TradeStatus.SELECTED);

        // Загружаем начальный набор пар
        List<PairData> initialPairs = fetchPairsProcessor.fetchPairs();

        // Запускаем первые MAX_ACTIVE_PAIRS пар
        initialPairs.stream()
                .limit(MAX_ACTIVE_PAIRS)
                .forEach(testTradeProcessor::testTrade);
    }

    private void simulationStep() {
        try {
            // 1. Получаем текущие торгуемые пары
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);

            // 2. Обновляем данные и проверяем условия выхода
            tradingPairs.forEach(testTradeProcessor::testTrade);

            // 3. Добираем новые пары до MAX_ACTIVE_PAIRS
            if (tradingPairs.size() < MAX_ACTIVE_PAIRS) {
                int neededPairs = MAX_ACTIVE_PAIRS - tradingPairs.size();
                fetchAndStartNewPairs(neededPairs);
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