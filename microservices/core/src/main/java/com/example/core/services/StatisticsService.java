package com.example.core.services;

import com.example.core.repositories.TradeHistoryRepository;
import com.example.shared.dto.TradePairsStatisticsDto;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradingPair;
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
    private final TradingPairService tradingPairService;
    private final TradeHistoryService tradeHistoryService;

    public TradePairsStatisticsDto collectStatistics() {
        BigDecimal unrealizedProfitUSDTToday = tradingPairService.findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus.TRADING).stream()
                .map(TradingPair::getProfitUSDTChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedProfitUSDTTotal = tradingPairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).stream()
                .map(TradingPair::getProfitUSDTChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedProfitPercentToday = tradingPairService.findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus.TRADING).stream()
                .map(TradingPair::getProfitPercentChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedProfitPercentTotal = tradingPairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).stream()
                .map(TradingPair::getProfitPercentChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal realizedProfitUSDTToday = Optional.ofNullable(tradeHistoryService.getSumRealizedProfitUSDTToday()).orElse(BigDecimal.ZERO);
        BigDecimal realizedProfitUSDTTotal = Optional.ofNullable(tradeHistoryService.getSumRealizedProfitUSDTTotal()).orElse(BigDecimal.ZERO);

        BigDecimal realizedProfitPercentToday = Optional.ofNullable(tradeHistoryService.getSumRealizedProfitPercentToday()).orElse(BigDecimal.ZERO);
        BigDecimal realizedProfitPercentTotal = Optional.ofNullable(tradeHistoryService.getSumRealizedProfitPercentTotal()).orElse(BigDecimal.ZERO);

        BigDecimal combinedProfitUSDTToday = unrealizedProfitUSDTToday.add(realizedProfitUSDTToday);
        BigDecimal combinedProfitUSDTTotal = unrealizedProfitUSDTTotal.add(realizedProfitUSDTTotal);

        BigDecimal combinedProfitPercentToday = unrealizedProfitPercentToday.add(realizedProfitPercentToday);
        BigDecimal combinedProfitPercentTotal = unrealizedProfitPercentTotal.add(realizedProfitPercentTotal);

        return TradePairsStatisticsDto.builder()

                .tradePairsWithErrorToday(tradeHistoryRepository.getByStatusForToday(TradeStatus.ERROR.name()))
                .tradePairsWithErrorTotal(tradeHistoryRepository.getByStatusTotal(TradeStatus.ERROR))

                .tradePairsToday(tradeHistoryRepository.getTradesToday())
                .tradePairsTotal(tradeHistoryRepository.getTradesTotal())

                .avgProfitUSDTToday(tradeHistoryRepository.getAvgProfitUSDTToday())
                .avgProfitUSDTTotal(tradeHistoryRepository.getAvgProfitUSDTTotal())

                .avgProfitPercentToday(tradeHistoryRepository.getAvgProfitPercentToday())
                .avgProfitPercentTotal(tradeHistoryRepository.getAvgProfitPercentTotal())

                .sumProfitUSDTToday(tradeHistoryRepository.getSumProfitUSDTToday())
                .sumProfitUSDTTotal(tradeHistoryRepository.getSumProfitUSDTTotal())

                .sumProfitPercentToday(tradeHistoryRepository.getSumProfitPercentToday())
                .sumProfitPercentTotal(tradeHistoryRepository.getSumProfitPercentTotal())

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

                .exitByBreakevenToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_BREAKEVEN.name()))
                .exitByBreakevenTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_BREAKEVEN.name()))

                .exitByNegativeZMinProfitToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_BY_NEGATIVE_Z_MIN_PROFIT.name()))
                .exitByNegativeZMinProfitTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_BY_NEGATIVE_Z_MIN_PROFIT.name()))

                .exitByManuallyToday(tradeHistoryRepository.getByExitReasonForToday(ExitReasonType.EXIT_REASON_MANUALLY.name()))
                .exitByManuallyTotal(tradeHistoryRepository.getAllByExitReason(ExitReasonType.EXIT_REASON_MANUALLY.name()))


                .sumProfitUnrealizedUSDTToday(unrealizedProfitUSDTToday)
                .sumProfitUnrealizedUSDTTotal(unrealizedProfitUSDTTotal)

                .sumProfitUnrealizedPercentToday(unrealizedProfitPercentToday)
                .sumProfitUnrealizedPercentTotal(unrealizedProfitPercentTotal)

                .sumProfitRealizedUSDTToday(realizedProfitUSDTToday)
                .sumProfitRealizedUSDTTotal(realizedProfitUSDTTotal)

                .sumProfitRealizedPercentToday(realizedProfitPercentToday)
                .sumProfitRealizedPercentTotal(realizedProfitPercentTotal)

                .sumProfitCombinedUSDTToday(combinedProfitUSDTToday)
                .sumProfitCombinedUSDTTotal(combinedProfitUSDTTotal)

                .sumProfitCombinedPercentToday(combinedProfitPercentToday)
                .sumProfitCombinedPercentTotal(combinedProfitPercentTotal)

                .build();
    }
}
