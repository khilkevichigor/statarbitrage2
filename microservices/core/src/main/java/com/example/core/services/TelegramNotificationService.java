package com.example.core.services;

import com.example.shared.events.NotificationEvent;
import com.example.shared.models.TradingPair;
import com.example.shared.utils.EventPublisher;
import com.example.shared.utils.TimeFormatterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.example.shared.utils.BigDecimalUtil.safeScale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService implements NotificationService {
    private static final String EMOJI_GREEN = "🟢";
    private static final String EMOJI_RED = "🔴";

    private final EventPublisher eventPublisher;

    @Override
    public void notifyClose(TradingPair tradingPair) {
        String message = formatCloseMessage(tradingPair);
        NotificationEvent event = new NotificationEvent(
                message,
                "test_user",
                NotificationEvent.NotificationType.TELEGRAM,
                NotificationEvent.Priority.HIGH
        );
        sendEvent(event);
    }

    private void sendEvent(NotificationEvent event) {
        log.debug("Отправка сообщения в телеграм {}", event.toString());
        try {
            eventPublisher.publish("notification-events-out-0", event);
        } catch (Exception e) {
            log.error("Ошибка отправки сообщения в телеграм {}", e.getMessage(), e);
        }
    }

    private String formatCloseMessage(TradingPair tradingPair) {
        BigDecimal delta = tradingPair.getPortfolioAfterTradeUSDT()
                .subtract(tradingPair.getPortfolioBeforeTradeUSDT());

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
                tradingPair.getPairName(),
                tradingPair.getProfitUSDTChanges().compareTo(BigDecimal.ZERO) >= 0 ? EMOJI_GREEN : EMOJI_RED, tradingPair.getProfitUSDTChanges(), tradingPair.getProfitPercentChanges(),
                tradingPair.getPortfolioBeforeTradeUSDT(), tradingPair.getPortfolioAfterTradeUSDT(),
                deltaString.startsWith("-") ? EMOJI_RED : EMOJI_GREEN, deltaString,
                TimeFormatterUtil.formatDurationFromMillis(tradingPair.getUpdatedTime() - tradingPair.getEntryTime()),
                tradingPair.getExitReason(),
                tradingPair.getUuid()
        );
    }
}
