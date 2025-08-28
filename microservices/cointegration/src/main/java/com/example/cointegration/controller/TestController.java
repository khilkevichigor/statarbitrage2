package com.example.cointegration.controller;

import com.example.cointegration.messaging.SendEventService;
import com.example.shared.events.CointegrationEvent;
import com.example.shared.events.CoreEvent;
import com.example.shared.events.CsvEvent;
import com.example.shared.events.NotificationEvent;
import com.example.shared.models.CointPair;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final SendEventService sendEventService;

    @GetMapping("/test-send-notification")
    public String testNotification() {
        sendEventService.sendNotificationEvent(new NotificationEvent(
                "🎉 Система работает!",
                "test_user",
                NotificationEvent.Priority.HIGH,
                NotificationEvent.Type.TELEGRAM
        ));
        return "Событие отправлено!";
    }

    @GetMapping("/test_send-csv")
    public String testCsv() {
        TradingPair tradingPair = new TradingPair();
        tradingPair.setPairName("testPair");
        sendEventService.sendCsvEvent(new CsvEvent(tradingPair, CsvEvent.Type.EXPORT_CLOSED_PAIR));
        return "Событие отправлено!";
    }

    @GetMapping("/test-send-coint")
    public String testCointegration() {
        CointPair cointPair = new CointPair();
        cointPair.setId(1L);
        cointPair.setUuid(UUID.randomUUID());
        cointPair.setPairName("test-pair");
        sendEventService.sendCointegrationEvent(new CointegrationEvent(Collections.singletonList(cointPair), CoreEvent.Type.NEW_COINT_PAIRS));
        return "Событие отправлено!";
    }
}