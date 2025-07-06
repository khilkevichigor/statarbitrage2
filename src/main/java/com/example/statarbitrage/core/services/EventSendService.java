package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.events.SendAsPhotoEvent;
import com.example.statarbitrage.common.events.SendAsTextEvent;
import com.example.statarbitrage.common.events.UpdateUiEvent;
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

    public void updateUI(UpdateUiEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
