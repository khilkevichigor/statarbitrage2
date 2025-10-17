package com.example.cointegration.controller;

import com.example.cointegration.messaging.SendEventService;
import com.example.shared.events.rabbit.CointegrationEvent;
import com.example.shared.models.Pair;
import com.example.shared.enums.PairType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final SendEventService sendEventService;

    //port:8086
    @GetMapping("/test-core")
    public String testCore() {
        Pair cointPair = new Pair();
        cointPair.setId(1L);
        cointPair.setUuid(UUID.randomUUID());
        cointPair.setPairName("test-pair");
        cointPair.setType(PairType.COINTEGRATED);
        sendEventService.sendCointegrationEvent(new CointegrationEvent(Collections.singletonList(cointPair), CointegrationEvent.Type.NEW_COINT_PAIRS));
        return "Событие отправлено!";
    }
}