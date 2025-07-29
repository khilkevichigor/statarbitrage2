package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.TradePairsStatisticsDto;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.repositories.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public TradePairsStatisticsDto collectStatistics() {
        BigDecimal unrealizedProfitUSDT = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).stream()
                .map(PairData::getProfitUSDTChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedProfitPercent = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).stream()
                .map(PairData::getProfitPercentChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal realized = Optional.ofNullable(tradeHistoryService.getSumRealizedProfit()).orElse(BigDecimal.ZERO);

        BigDecimal combined = unrealizedProfitPercent.add(realized);

        return TradePairsStatisticsDto.builder()

                .tradePairsWithErrorToday(tradeHistoryRepository.getByStatusForToday(TradeStatus.ERROR))
                .tradePairsWithErrorTotal(tradeHistoryRepository.getByStatusTotal(TradeStatus.ERROR))

                .tradePairsToday(tradeHistoryRepository.getTradesToday())
                .tradePairsTotal(tradeHistoryRepository.getTradesTotal())

                .avgProfitToday(tradeHistoryRepository.getAvgProfitToday())
                .avgProfitTotal(tradeHistoryRepository.getAvgProfitTotal())

                .sumProfitUSDTToday(tradeHistoryRepository.getSumProfitUSDTToday())
                .sumProfitUSDTTotal(tradeHistoryRepository.getSumProfitUSDTTotal())

                .sumProfitPercentToday(tradeHistoryRepository.getSumProfitPercentToday())
                .sumProfitPercentTotal(tradeHistoryRepository.getSumProfitPercentTotal())

                .exitByStopToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_STOP.getDescription()))
                .exitByStopTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_STOP.getDescription()))

                .exitByTakeToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_TAKE.getDescription()))
                .exitByTakeTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_TAKE.getDescription()))

                .exitByZMinToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_Z_MIN.getDescription()))
                .exitByZMinTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_Z_MIN.getDescription()))

                .exitByZMaxToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_Z_MAX.getDescription()))
                .exitByZMaxTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_Z_MAX.getDescription()))

                .exitByTimeToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_TIME.getDescription()))
                .exitByTimeTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_TIME.getDescription()))

                .exitByManuallyToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_MANUALLY.getDescription()))
                .exitByManuallyTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_MANUALLY.getDescription()))

                .sumProfitUnrealizedUSDT(unrealizedProfitUSDT)
                .sumProfitUnrealizedPercent(unrealizedProfitPercent)
                .sumProfitRealized(realized)
                .sumProfitCombined(combined)

                .build();
    }
}
