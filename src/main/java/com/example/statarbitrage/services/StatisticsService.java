package com.example.statarbitrage.services;

import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.TradeStatisticsDto;
import com.example.statarbitrage.repositories.PairDataRepository;
import com.example.statarbitrage.repositories.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.example.statarbitrage.constant.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {
    private final EventSendService eventSendService;

    private final PairDataRepository pairDataRepository;
    private final TradeLogRepository tradeLogRepository;

    @EventListener(ApplicationReadyEvent.class) //postConstruct не сработает тк бд не готова еще
    @Transactional
    public void deleteUnfinishedTrades() { //очищаем чтобы бд была актуальной даже после стопа приложения с незавершенным трейдом
        int deleted = tradeLogRepository.deleteUnfinishedTrades();
        log.info("🧹 Удалено {} незавершённых трейдов", deleted);
    }

    public void printTradeStatistics(String chatId) {
        TradeStatisticsDto stats = collectStatistics();
        String message = formatStatistics(stats);
        log.info(message);
        sendMessage(chatId, message, true);
    }

    public String formatStatistics(TradeStatisticsDto stats) {
        return String.format("""
                        📊 TRADE STATS today / total:
                        - Trades: %d / %d
                        
                        📈 Avg profit:
                        - Avg: %s%% / %s%%
                        
                        📈 Sum profit:
                        - Sum: %s%% / %s%%
                        
                        ❌ Exit Reasons:
                        - STOP: %d / %d
                        - TAKE: %d / %d
                        - Z min: %d / %d
                        - Z max: %d / %d
                        - Time: %d / %d
                        """,
                stats.getTradesToday(),
                stats.getTradesTotal(),

                format(stats.getAvgProfitToday()),
                format(stats.getAvgProfitTotal()),

                format(stats.getSumProfitToday()),
                format(stats.getSumProfitTotal()),

                stats.getExitByStopToday(),
                stats.getExitByStopTotal(),

                stats.getExitByTakeToday(),
                stats.getExitByTakeTotal(),

                stats.getExitByZMinToday(),
                stats.getExitByZMinTotal(),

                stats.getExitByZMaxToday(),
                stats.getExitByZMaxTotal(),

                stats.getExitByTimeToday(),
                stats.getExitByTimeTotal()
        );
    }

    // Вспомогательный метод для округления BigDecimal до 2 знаков после запятой
    private String format(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).toString();
    }

    public TradeStatisticsDto collectStatistics() {
        return TradeStatisticsDto.builder()
                .tradesToday(tradeLogRepository.getTradesToday())
                .tradesTotal(tradeLogRepository.getTradesTotal())

                .avgProfitToday(tradeLogRepository.getAvgProfitToday())
                .avgProfitTotal(tradeLogRepository.getAvgProfitTotal())

                .sumProfitToday(tradeLogRepository.getSumProfitToday())
                .sumProfitTotal(tradeLogRepository.getSumProfitTotal())

//                .exitByStopToday(tradeLogRepository.getExitByStopToday())
//                .exitByStopTotal(tradeLogRepository.getExitByStopTotal())
                .exitByStopToday(tradeLogRepository.getExitByToday(EXIT_REASON_BY_STOP))
                .exitByStopTotal(tradeLogRepository.getExitByTotal(EXIT_REASON_BY_STOP))

//                .exitByTakeToday(tradeLogRepository.getExitByTakeToday())
//                .exitByTakeTotal(tradeLogRepository.getExitByTakeTotal())
                .exitByTakeToday(tradeLogRepository.getExitByToday(EXIT_REASON_BY_TAKE))
                .exitByTakeTotal(tradeLogRepository.getExitByTotal(EXIT_REASON_BY_TAKE))

//                .exitByZMinToday(tradeLogRepository.getExitByZMinToday())
//                .exitByZMinTotal(tradeLogRepository.getExitByZMinTotal())
                .exitByZMinToday(tradeLogRepository.getExitByToday(EXIT_REASON_BY_Z_MIN))
                .exitByZMinTotal(tradeLogRepository.getExitByTotal(EXIT_REASON_BY_Z_MIN))

//                .exitByZMaxToday(tradeLogRepository.getExitByZMaxToday())
//                .exitByZMaxTotal(tradeLogRepository.getExitByZMaxTotal())
                .exitByZMaxToday(tradeLogRepository.getExitByToday(EXIT_REASON_BY_Z_MAX))
                .exitByZMaxTotal(tradeLogRepository.getExitByTotal(EXIT_REASON_BY_Z_MAX))

//                .exitByTimeToday(tradeLogRepository.getExitByTimeToday())
//                .exitByTimeTotal(tradeLogRepository.getExitByTimeTotal())
                .exitByTimeToday(tradeLogRepository.getExitByToday(EXIT_REASON_BY_TIME))
                .exitByTimeTotal(tradeLogRepository.getExitByTotal(EXIT_REASON_BY_TIME))

                .exitByManuallyToday(tradeLogRepository.getExitByToday(EXIT_REASON_MANUALLY))
                .exitByManuallyTotal(tradeLogRepository.getExitByTotal(EXIT_REASON_MANUALLY))

                .build();
    }

    public void sendMessage(String chatId, String text, boolean withLogging) {
        try {
            eventSendService.sendTelegramMessageAsTextEvent(SendAsTextEvent.builder()
                    .chatId(chatId)
                    .enableMarkdown(true)
                    .text(text)
                    .build());
            if (withLogging) {
                log.info("📤 Стата отправлена в Telegram");
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при отправке статы: {}", e.getMessage(), e);
        }
    }
}
