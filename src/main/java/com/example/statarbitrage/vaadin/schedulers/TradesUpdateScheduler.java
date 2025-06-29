package com.example.statarbitrage.vaadin.schedulers;

import com.example.statarbitrage.events.UpdateUiEvent;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.services.EventSendService;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.vaadin.processors.TestTradeProcessor;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradesUpdateScheduler {
    private final PairDataService pairDataService;
    private final TestTradeProcessor testTradeProcessor;
    private final EventSendService eventSendService;

    //    @Scheduled(cron = "5 * * * * *")
//    @Scheduled(fixedRate = 1 * 60 * 1_000)
    public void updateTrades() {
        log.info("Starting update trades by scheduler...");
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