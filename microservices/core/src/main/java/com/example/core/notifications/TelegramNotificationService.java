package com.example.core.notifications;

import com.example.core.bot.BotConfig;
import com.example.core.common.events.SendAsTextEvent;
import com.example.core.common.model.PairData;
import com.example.core.core.services.EventSendService;
import com.example.core.formatters.TimeFormatterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.example.core.common.utils.BigDecimalUtil.safeScale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService implements NotificationService {
    private static final String EMOJI_GREEN = "🟢";
    private static final String EMOJI_RED = "🔴";

    private final EventSendService eventSendService;
    private final BotConfig botConfig;

    @Override
    public void notifyOpen(PairData pairData) {
        String message = formatOpenMessage(pairData);
        sendNotification(message);
    }

    @Override
    public void notifyClose(PairData pairData) {
        String message = formatCloseMessage(pairData);
        sendNotification(message);
    }

    private void sendNotification(String text) {
        SendAsTextEvent event = SendAsTextEvent.builder()
                .chatId(String.valueOf(botConfig.getOwnerChatId()))
                .text(text)
                .enableMarkdown(false)
                .build();
        log.debug("Отправка сообщения в телеграм {}", event.toString());
        try {
            eventSendService.sendTelegramMessageAsTextEvent(event);
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения в телеграм {}", e.getMessage(), e);
        }
    }

    private String formatOpenMessage(PairData pairData) {
        return String.format(
                "Пара открыта\n" +
                        "%s\n" +
                        "%s",
                pairData.getPairName(),
                pairData.getUuid()
        );
    }

    private String formatCloseMessage(PairData pairData) {
        BigDecimal delta = pairData.getPortfolioAfterTradeUSDT()
                .subtract(pairData.getPortfolioBeforeTradeUSDT());

        String deltaString = delta.compareTo(BigDecimal.ZERO) >= 0
                ? "+" + safeScale(delta, 2)
                : safeScale(delta, 2).toPlainString();
        return String.format(
                """
                        Пара закрыта
                        %s
                        Профит: %s %.2f USDT (%.2f%%)
                        Баланс: было %.2f $, стало: %.2f $
                        Дельта баланса: %s %s $
                        Продолжительность: %s
                        %s
                        %s""",
                pairData.getPairName(),
                pairData.getProfitUSDTChanges().compareTo(BigDecimal.ZERO) >= 0 ? EMOJI_GREEN : EMOJI_RED, pairData.getProfitUSDTChanges(), pairData.getProfitPercentChanges(),
                pairData.getPortfolioBeforeTradeUSDT(), pairData.getPortfolioAfterTradeUSDT(),
                deltaString.startsWith("-") ? EMOJI_RED : EMOJI_GREEN, deltaString,
                TimeFormatterUtil.formatDurationFromMillis(pairData.getUpdatedTime() - pairData.getEntryTime()),
                pairData.getExitReason(),
                pairData.getUuid()
        );
    }
}
