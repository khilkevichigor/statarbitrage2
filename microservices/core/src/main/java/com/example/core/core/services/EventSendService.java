package com.example.core.core.services;

import com.example.shared.events.UpdateUiEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSendService {
    private final ApplicationEventPublisher applicationEventPublisher;

    public void updateUI(UpdateUiEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}