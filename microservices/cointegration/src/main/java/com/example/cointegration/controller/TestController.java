package com.example.cointegration.controller;

import com.example.cointegration.messaging.SendEventService;
import com.example.shared.events.CointegrationEvent;
import com.example.shared.models.CointPair;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final SendEventService sendEventService;

    @GetMapping("/test-core")
    public String testCore() {
        CointPair cointPair = new CointPair();
        cointPair.setId(1L);
        cointPair.setUuid(UUID.randomUUID());
        cointPair.setPairName("test-pair");
        sendEventService.sendCointegrationEvent(new CointegrationEvent(Collections.singletonList(cointPair), CointegrationEvent.Type.NEW_COINT_PAIRS));
        return "Событие отправлено!";
    }
}