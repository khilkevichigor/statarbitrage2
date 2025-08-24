package com.example.core.controllers;

import com.example.shared.events.CsvEvent;
import com.example.shared.events.NotificationEvent;
import com.example.shared.models.PairData;
import com.example.shared.utils.EventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private EventPublisher eventPublisher;

    @GetMapping("/test1")
    public String testNotification() {
        NotificationEvent event = new NotificationEvent(
                "🎉 Система работает!",
                "test_user",
                NotificationEvent.NotificationType.TELEGRAM,
                NotificationEvent.Priority.HIGH
        );
        eventPublisher.publish("notification-events-out-0", event);
        return "Событие отправлено!";
    }

    @GetMapping("/test2")
    public String testCsv() {
        PairData pairData = new PairData();
        pairData.setPairName("testPair");
        CsvEvent event = new CsvEvent(
                pairData
        );
        eventPublisher.publish("csv-events-out-0", event);
        return "Событие отправлено!";
    }
}