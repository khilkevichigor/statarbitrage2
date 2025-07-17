package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.TradeStatisticsDto;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.repositories.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final TradeLogRepository tradeLogRepository;
    private final PairDataService pairDataService;
    private final TradeLogService tradeLogService;

    @EventListener(ApplicationReadyEvent.class) //postConstruct не сработает тк бд не готова еще
    @Transactional
    public void deleteUnfinishedTrades() { //очищаем чтобы бд была актуальной даже после стопа приложения с незавершенным трейдом
        int deleted = tradeLogRepository.deleteUnfinishedTrades();
        log.info("🧹 Удалено {} незавершённых трейдов", deleted);
    }

    public TradeStatisticsDto collectStatistics() {
        BigDecimal unrealized = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).stream()
                .map(PairData::getProfitChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal realized = Optional.ofNullable(tradeLogService.getSumRealizedProfit()).orElse(BigDecimal.ZERO);

        BigDecimal combined = unrealized.add(realized);

        return TradeStatisticsDto.builder()

                .tradesToday(tradeLogRepository.getTradesToday())
                .tradesTotal(tradeLogRepository.getTradesTotal())

                .avgProfitToday(tradeLogRepository.getAvgProfitToday())
                .avgProfitTotal(tradeLogRepository.getAvgProfitTotal())

                .sumProfitToday(tradeLogRepository.getSumProfitToday())
                .sumProfitTotal(tradeLogRepository.getSumProfitTotal())

                .exitByStopToday(tradeLogRepository.getExitByToday(ExitReasonType.EXIT_REASON_BY_STOP.name()))
                .exitByStopTotal(tradeLogRepository.getExitByTotal(ExitReasonType.EXIT_REASON_BY_STOP.name()))

                .exitByTakeToday(tradeLogRepository.getExitByToday(ExitReasonType.EXIT_REASON_BY_TAKE.name()))
                .exitByTakeTotal(tradeLogRepository.getExitByTotal(ExitReasonType.EXIT_REASON_BY_TAKE.name()))

                .exitByZMinToday(tradeLogRepository.getExitByToday(ExitReasonType.EXIT_REASON_BY_Z_MIN.name()))
                .exitByZMinTotal(tradeLogRepository.getExitByTotal(ExitReasonType.EXIT_REASON_BY_Z_MIN.name()))

                .exitByZMaxToday(tradeLogRepository.getExitByToday(ExitReasonType.EXIT_REASON_BY_Z_MAX.name()))
                .exitByZMaxTotal(tradeLogRepository.getExitByTotal(ExitReasonType.EXIT_REASON_BY_Z_MAX.name()))

                .exitByTimeToday(tradeLogRepository.getExitByToday(ExitReasonType.EXIT_REASON_BY_TIME.name()))
                .exitByTimeTotal(tradeLogRepository.getExitByTotal(ExitReasonType.EXIT_REASON_BY_TIME.name()))

                .exitByManuallyToday(tradeLogRepository.getExitByToday(ExitReasonType.EXIT_REASON_MANUALLY.name()))
                .exitByManuallyTotal(tradeLogRepository.getExitByTotal(ExitReasonType.EXIT_REASON_MANUALLY.name()))

                .sumProfitUnrealized(unrealized)
                .sumProfitRealized(realized)
                .sumProfitCombined(combined)

                .build();
    }
}
