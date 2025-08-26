package com.example.core.services;

import com.example.shared.events.CsvEvent;
import com.example.shared.models.TradingPair;
import com.example.shared.utils.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvExportService {
    private final EventPublisher eventPublisher;

    public synchronized void appendPairDataToCsv(TradingPair tradingPair) {
        CsvEvent event = new CsvEvent(
                tradingPair
        );
        sendEvent(event);
    }

    private void sendEvent(CsvEvent event) {
        log.debug("Отправка события {}", event.toString());
        try {
            eventPublisher.publish("csv-events-out-0", event);
        } catch (Exception e) {
            log.error("Ошибка отправки события {}", e.getMessage(), e);
        }
    }
}