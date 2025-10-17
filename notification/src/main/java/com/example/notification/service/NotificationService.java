package com.example.notification.service;

import com.example.shared.models.Pair;
import org.springframework.stereotype.Service;

@Service
public interface NotificationService {
    void sendTelegramClosedPair(Pair tradingPair);

    void sendTelegramMessage(String message);
}
