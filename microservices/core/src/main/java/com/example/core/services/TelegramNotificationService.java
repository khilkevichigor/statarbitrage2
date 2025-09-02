//package com.example.core.services;
//
//import com.example.core.messaging.SendEventService;
//import com.example.shared.events.rabbit.CoreEvent;
//import com.example.shared.models.TradingPair;
//import com.example.shared.utils.TimeFormatterUtil;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.math.BigDecimal;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class TelegramNotificationService implements NotificationService {
//    private static final String EMOJI_GREEN = "🟢";
//    private static final String EMOJI_RED = "🔴";
//
//    private final SendEventService sendEventService;
//
//    @Override
//    public void sendTelegramClosedPair(TradingPair tradingPair) {
//        String message = formatCloseMessage(tradingPair);
//        sendEventService.sendCoreEvent(new CoreEvent(message, "test_user", CoreEvent.Priority.HIGH, CoreEvent.Type.MESSAGE_TO_TELEGRAM));
//    }
//
//    @Override
//    public void sendTelegramMessage(String message) {
//        sendEventService.sendCoreEvent(new CoreEvent(
//                message,
//                "test_user",
//                CoreEvent.Priority.HIGH,
//                CoreEvent.Type.MESSAGE_TO_TELEGRAM
//        ));
//    }
//
//    private String formatCloseMessage(TradingPair tradingPair) {
//        return String.format(
//                """
//                        Пара закрыта
//                        %s
//                        Профит: %s %.2f USDT (%.2f%%)
//                        Баланс: было %.2f $, стало: %.2f $
//                        Продолжительность: %s
//                        %s
//                        %s""",
//                tradingPair.getPairName(),
//                tradingPair.getProfitUSDTChanges().compareTo(BigDecimal.ZERO) >= 0 ? EMOJI_GREEN : EMOJI_RED, tradingPair.getProfitUSDTChanges(), tradingPair.getProfitPercentChanges(),
//                tradingPair.getPortfolioBeforeTradeUSDT(), tradingPair.getPortfolioAfterTradeUSDT(),
//                TimeFormatterUtil.formatDurationFromMillis(tradingPair.getUpdatedTime() - tradingPair.getEntryTime()),
//                tradingPair.getExitReason(),
//                tradingPair.getUuid()
//        );
//    }
//}
