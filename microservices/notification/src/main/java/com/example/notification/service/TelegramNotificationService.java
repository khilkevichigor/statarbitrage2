package com.example.notification.service;

import com.example.notification.bot.BotConfig;
import com.example.notification.events.SendAsTextEvent;
import com.example.shared.models.TradingPair;
import com.example.shared.utils.TimeFormatterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService implements NotificationService {
    private final BotConfig botConfig;
    private final EventSendService eventSendService;

    private static final String EMOJI_GREEN = "🟢";
    private static final String EMOJI_RED = "🔴";

    @Override
    public void sendTelegramClosedPair(TradingPair tradingPair) {
        String message = formatCloseMessage(tradingPair);
        sendNotification(message);
    }

    @Override
    public void sendTelegramMessage(String message) {
        sendNotification(message);
    }

    private void sendNotification(String text) {
        SendAsTextEvent event = SendAsTextEvent.builder()
                .chatId(String.valueOf(botConfig.getOwnerChatId()))
                .text(text)
                .enableMarkdown(false)
                .build();
        try {
            eventSendService.sendTelegramMessageAsTextEvent(event);
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения в телеграм {}", e.getMessage(), e);
        }
    }

    private String formatCloseMessage(TradingPair tradingPair) {
        return String.format(
                """
                        Пара закрыта
                        %s
                        Профит: %s %.2f USDT (%.2f%%)
                        Продолжительность: %s
                        %s
                        %s""",
                tradingPair.getPairName(),
                tradingPair.getProfitUSDTChanges().compareTo(BigDecimal.ZERO) >= 0 ? EMOJI_GREEN : EMOJI_RED, tradingPair.getProfitUSDTChanges(), tradingPair.getProfitPercentChanges(),
                TimeFormatterUtil.formatDurationFromMillis(tradingPair.getUpdatedTime() - tradingPair.getEntryTime()),
                tradingPair.getExitReason(),
                tradingPair.getUuid()
        );
    }
}
