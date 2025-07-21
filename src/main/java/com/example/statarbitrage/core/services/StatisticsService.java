package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.TradeStatisticsDto;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.repositories.TradeHistoryRepository;
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

    private final TradeHistoryRepository tradeHistoryRepository;
    private final PairDataService pairDataService;
    private final TradeHistoryService tradeHistoryService;

    @EventListener(ApplicationReadyEvent.class) //postConstruct не сработает тк бд не готова еще
    @Transactional
    public void deleteUnfinishedTrades() { //очищаем чтобы бд была актуальной даже после стопа приложения с незавершенным трейдом
        int deleted = tradeHistoryRepository.deleteUnfinishedTrades();
        log.info("🧹 Удалено {} незавершённых трейдов", deleted);
    }

    public TradeStatisticsDto collectStatistics() {
        BigDecimal unrealized = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).stream()
                .map(PairData::getProfitChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal realized = Optional.ofNullable(tradeHistoryService.getSumRealizedProfit()).orElse(BigDecimal.ZERO);

        BigDecimal combined = unrealized.add(realized);

        return TradeStatisticsDto.builder()

                .tradesToday(tradeHistoryRepository.getTradesToday())
                .tradesTotal(tradeHistoryRepository.getTradesTotal())

                .avgProfitToday(tradeHistoryRepository.getAvgProfitToday())
                .avgProfitTotal(tradeHistoryRepository.getAvgProfitTotal())

                .sumProfitToday(tradeHistoryRepository.getSumProfitToday())
                .sumProfitTotal(tradeHistoryRepository.getSumProfitTotal())

                .exitByStopToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_STOP.name()))
                .exitByStopTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_STOP.name()))

                .exitByTakeToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_TAKE.name()))
                .exitByTakeTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_TAKE.name()))

                .exitByZMinToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_Z_MIN.name()))
                .exitByZMinTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_Z_MIN.name()))

                .exitByZMaxToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_Z_MAX.name()))
                .exitByZMaxTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_Z_MAX.name()))

                .exitByTimeToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_TIME.name()))
                .exitByTimeTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_TIME.name()))

                .exitByManuallyToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_MANUALLY.name()))
                .exitByManuallyTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_MANUALLY.name()))

                .sumProfitUnrealized(unrealized)
                .sumProfitRealized(realized)
                .sumProfitCombined(combined)

                .build();
    }
}
