package com.example.statarbitrage.notifications;

import com.example.statarbitrage.bot.BotConfig;
import com.example.statarbitrage.common.events.SendAsTextEvent;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.core.services.EventSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.example.statarbitrage.common.utils.BigDecimalUtil.safeScale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService implements NotificationService {
    private static final String EMOJI_GREEN = "🟢";
    private static final String EMOJI_RED = "🔴";

    private final EventSendService eventSendService;
    private final BotConfig botConfig;

//    @Override
//    public void notifyOpen(PairData pairData) {
//        eventSendService.sendTelegramMessageAsTextEvent(
//                SendAsTextEvent.builder()
//                        .chatId(String.valueOf(botConfig.getOwnerChatId()))
//                        .text(String.format(
//                                "Пара открыта\n*%s*\n%s",
//                                pairData.getPairName(),
//                                pairData.getUuid()
//                        ))
//                        .enableMarkdown(true)
//                        .build()
//        );
//    }
//
//    @Override
//    public void notifyClose(PairData pairData) {
//        eventSendService.sendTelegramMessageAsTextEvent(
//                SendAsTextEvent.builder()
//                        .chatId(String.valueOf(botConfig.getOwnerChatId()))
//                        .text(String.format(
//                                "%s Пара закрыта\n*%s*\n%.2f USDT (%.2f%%)\n%s\n%s",
//                                pairData.getProfitPercentChanges().compareTo(BigDecimal.ZERO) >= 0 ? "🟢" : "🔴",
//                                pairData.getPairName(),
//                                pairData.getProfitUSDTChanges(),
//                                pairData.getProfitPercentChanges(),
//                                pairData.getExitReason(),
//                                pairData.getUuid()
//                        ))
//                        .enableMarkdown(true)
//                        .build()
//        );
//    }

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
        log.info("Отправка сообщения в телеграм {}", event.toString());
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
                : "-" + safeScale(delta, 2);
        return String.format(
                """
                        %s Пара закрыта
                        %s
                        %.2f USDT (%.2f%%)
                        было: %.2f $, стало: %.2f $
                        дельта: %s $
                        %s
                        %s""",
                pairData.getProfitPercentChanges().compareTo(BigDecimal.ZERO) >= 0 ? EMOJI_GREEN : EMOJI_RED,
                pairData.getPairName(),
                pairData.getProfitUSDTChanges(),
                pairData.getProfitPercentChanges(),
                pairData.getPortfolioBeforeTradeUSDT(), pairData.getPortfolioAfterTradeUSDT(),
                deltaString,
                pairData.getExitReason(),
                pairData.getUuid()
        );
    }

    public static String escapeMarkdown(String text) {
        if (text == null) return "";
        return text
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`");
    }

}
