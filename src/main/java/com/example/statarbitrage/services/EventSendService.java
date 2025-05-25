package com.example.statarbitrage.services;

import com.example.statarbitrage.events.SendAsTextEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventSendService {
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public void sendAsText(SendAsTextEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
