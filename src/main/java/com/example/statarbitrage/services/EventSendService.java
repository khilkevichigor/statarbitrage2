package com.example.statarbitrage.services;

import com.example.statarbitrage.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSendService {
    private final ApplicationEventPublisher applicationEventPublisher;

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

    public void updateUI(UpdateUiEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
