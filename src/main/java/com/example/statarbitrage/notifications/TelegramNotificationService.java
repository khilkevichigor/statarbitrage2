package com.example.statarbitrage.notifications;

import com.example.statarbitrage.bot.BotConfig;
import com.example.statarbitrage.common.events.SendAsTextEvent;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.core.services.EventSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
                                "📉 Начата пара *%s*",
                                pairData.getPairName()
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
                                "📉 Закрыта пара *%s*\nПрофит: `%.2f` USDT (`%.2f%%`). Причина %s",
                                pairData.getPairName(),
                                pairData.getProfitUSDTChanges(),
                                pairData.getProfitPercentChanges(),
                                pairData.getExitReason()
                        ))
                        .enableMarkdown(true)
                        .build()
        );
    }
}
