package com.example.core.controllers;

import com.example.core.messaging.SendEventService;
import com.example.shared.events.CoreEvent;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final SendEventService sendEventService;

    @GetMapping("/test-telegram")
    public String testTelegram() {
        sendEventService.sendCoreEvent(new CoreEvent(
                "🎉 Система работает!",
                "test_user",
                CoreEvent.Priority.HIGH,
                CoreEvent.Type.MESSAGE_TO_TELEGRAM
        ));
        return "Событие отправлено!";
    }

    @GetMapping("/test-csv")
    public String testCsv() {
        TradingPair tradingPair = new TradingPair();
        tradingPair.setPairName("testPair");
        sendEventService.sendCoreEvent(new CoreEvent(
                Collections.singletonList(tradingPair),
                CoreEvent.Type.ADD_CLOSED_TO_CSV)
        );
        return "Событие отправлено!";
    }
}