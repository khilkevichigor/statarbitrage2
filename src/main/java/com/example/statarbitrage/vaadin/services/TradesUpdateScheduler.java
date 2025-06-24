package com.example.statarbitrage.vaadin.services;

import com.example.statarbitrage.events.UpdateUiEvent;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.PairDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradesUpdateScheduler {
    private final PairDataService pairDataService;
    private final TestTradeProcessor testTradeProcessor;
    private final EventSendService eventSendService;

    @Scheduled(fixedRate = 1 * 60 * 1_000)
    public void runSimulationStep() {
        try {
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (!tradingPairs.isEmpty()) {
                tradingPairs.forEach(testTradeProcessor::testTrade);
                // После обновления данных вызываем обновление UI
                eventSendService.updateUI(UpdateUiEvent.builder().build());
            }
        } catch (Exception e) {
            log.error("Ошибка обновления трейда", e);
        }
    }
}