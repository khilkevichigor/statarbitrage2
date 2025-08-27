package com.example.core.controllers;

import com.example.core.messaging.SendEventService;
import com.example.shared.events.CsvEvent;
import com.example.shared.events.NotificationEvent;
import com.example.shared.models.TradingPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private SendEventService sendEventService;

    @GetMapping("/test1")
    public String testNotification() {
        sendEventService.sendNotificationEvent(new NotificationEvent(
                "🎉 Система работает!",
                "test_user",
                NotificationEvent.Priority.HIGH,
                NotificationEvent.Type.TELEGRAM
        ));
        return "Событие отправлено!";
    }

    @GetMapping("/test2")
    public String testCsv() {
        TradingPair tradingPair = new TradingPair();
        tradingPair.setPairName("testPair");
        sendEventService.sendCsvEvent(new CsvEvent(tradingPair, CsvEvent.Type.EXPORT_CLOSED_PAIR));
        return "Событие отправлено!";
    }
}