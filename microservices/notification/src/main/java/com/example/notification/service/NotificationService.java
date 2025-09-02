package com.example.notification.service;

import com.example.shared.models.TradingPair;
import org.springframework.stereotype.Service;

@Service
public interface NotificationService {
    void sendTelegramClosedPair(TradingPair tradingPair);

    void sendTelegramMessage(String message);
}
