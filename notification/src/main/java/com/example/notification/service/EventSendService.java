package com.example.notification.service;

import com.example.notification.events.SendAsPhotoEvent;
import com.example.notification.events.SendAsTextEvent;
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

}
