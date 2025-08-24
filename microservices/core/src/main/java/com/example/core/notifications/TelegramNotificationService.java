package com.example.core.notifications;

import com.example.core.formatters.TimeFormatterUtil;
import com.example.shared.events.NotificationEvent;
import com.example.shared.models.PairData;
import com.example.shared.utils.EventPublisher;
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
    public void notifyClose(PairData pairData) {
        String message = formatCloseMessage(pairData);
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
