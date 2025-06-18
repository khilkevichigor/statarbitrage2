package com.example.statarbitrage.services;

import com.example.statarbitrage.events.ResetProfitEvent;
import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.events.StartNewTradeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventSendService {
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public void sendTelegramMessageAsTextEvent(SendAsTextEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void sendTelegramMessageAsPhotoEvent(SendAsPhotoEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void sendResetProfitEvent(ResetProfitEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void sendStartNewTradeEvent(StartNewTradeEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
