package com.example.statarbitrage.notifications;

import com.example.statarbitrage.bot.BotConfig;
import com.example.statarbitrage.common.events.SendAsTextEvent;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.core.services.EventSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService implements NotificationService {
    private final EventSendService eventSendService;
    private final BotConfig botConfig;

    @Override
    public void notifyOpen(PairData pairData) {
        eventSendService.sendTelegramMessageAsTextEvent(
                SendAsTextEvent.builder()
                        .chatId(String.valueOf(botConfig.getOwnerChatId()))
                        .text(String.format(
                                "Пара открыта\n*%s*\n%s",
                                pairData.getPairName(),
                                pairData.getUuid()
                        ))
                        .enableMarkdown(true)
                        .build()
        );
    }

    @Override
    public void notifyClose(PairData pairData) {
        eventSendService.sendTelegramMessageAsTextEvent(
                SendAsTextEvent.builder()
                        .chatId(String.valueOf(botConfig.getOwnerChatId()))
                        .text(String.format(
                                "%s Пара закрыта\n*%s*\n%.2f USDT (%.2f%%)\n%s\n%s",
                                pairData.getProfitPercentChanges().compareTo(BigDecimal.ZERO) >= 0 ? "🟢" : "🔴",
                                pairData.getPairName(),
                                pairData.getProfitUSDTChanges(),
                                pairData.getProfitPercentChanges(),
                                pairData.getExitReason(),
                                pairData.getUuid()
                        ))
                        .enableMarkdown(true)
                        .build()
        );
    }
}
