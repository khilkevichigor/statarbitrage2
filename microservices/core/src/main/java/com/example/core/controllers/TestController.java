package com.example.core.controllers;

import com.example.core.messaging.SendEventService;
import com.example.shared.events.rabbit.CoreEvent;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final SendEventService sendEventService;

    @GetMapping("/test-teleg")
    public String testTelegram2() {
        sendEventService.sendCoreEvent(new CoreEvent(
                "test",
                CoreEvent.Type.MESSAGE_TO_TELEGRAM
        ));
        return "Событие отправлено!";
    }

    @GetMapping("/test-csv")
    public String testCsv() {
        Pair tradingPair = new Pair();
        tradingPair.setPairName("testPair");
        sendEventService.sendCoreEvent(new CoreEvent(
                tradingPair,
                CoreEvent.Type.ADD_CLOSED_TO_CSV)
        );
        return "Событие отправлено!";
    }
}