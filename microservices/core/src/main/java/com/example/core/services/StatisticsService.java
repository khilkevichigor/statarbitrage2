package com.example.core.services;

import com.example.core.repositories.TradeHistoryRepository;
import com.example.shared.dto.PairAggregatedStatisticsDto;
import com.example.shared.dto.TradePairsStatisticsDto;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradingPair;
import com.example.shared.utils.TimeFormatterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

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

    public List<PairAggregatedStatisticsDto> getClosedPairsAggregatedStatistics() {
        try {
            List<TradingPair> closedPairs = tradingPairService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.CLOSED);

            Map<String, List<TradingPair>> pairGroups = closedPairs.stream()
                    .collect(Collectors.groupingBy(TradingPair::getPairName));

            return pairGroups.entrySet().stream()
                    .map(entry -> {
                        String pairName = entry.getKey();
                        List<TradingPair> pairs = entry.getValue();

                        return PairAggregatedStatisticsDto.builder()
                                .pairName(pairName)
                                .totalTrades(pairs.size())
                                .totalProfitUSDT(calculateTotalProfit(pairs))
                                .averageProfitPercent(calculateAverageProfitPercent(pairs))
                                .averageTradeDuration(calculateAverageTradeDuration(pairs))
                                .totalAveragingCount(calculateTotalAveragingCount(pairs))
                                .averageTimeToMinProfit(calculateAverageTimeToMinProfit(pairs))
                                .averageTimeToMaxProfit(calculateAverageTimeToMaxProfit(pairs))
                                .averageZScoreEntry(calculateAverageZScoreEntry(pairs))
                                .averageZScoreCurrent(calculateAverageZScoreCurrent(pairs))
                                .averageZScoreMax(calculateAverageZScoreMax(pairs))
                                .averageZScoreMin(calculateAverageZScoreMin(pairs))
                                .averageCorrelationEntry(calculateAverageCorrelationEntry(pairs))
                                .averageCorrelationCurrent(calculateAverageCorrelationCurrent(pairs))
                                .build();
                    })
                    .sorted((a, b) -> Long.compare(b.getTotalTrades(), a.getTotalTrades()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("❌ Ошибка получения агрегированной статистики по парам", e);
            return Collections.emptyList();
        }
    }

    private BigDecimal calculateTotalProfit(List<TradingPair> pairs) {
        return pairs.stream()
                .map(TradingPair::getProfitUSDTChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateAverageProfitPercent(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(TradingPair::getProfitPercentChanges)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ? BigDecimal.valueOf(average.getAsDouble()) : BigDecimal.ZERO;
    }

    private String calculateAverageTradeDuration(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .filter(p -> p.getEntryTime() != 0L && p.getUpdatedTime() != 0L)
                .mapToLong(p -> p.getUpdatedTime() - p.getEntryTime())
                .average();

        if (average.isPresent()) {
            return TimeFormatterUtil.formatDurationFromMillis((long) average.getAsDouble());
        }
        return "N/A";
    }

    private long calculateTotalAveragingCount(List<TradingPair> pairs) {
        return pairs.stream()
                .mapToInt(TradingPair::getAveragingCount)
                .sum();
    }

    private String calculateAverageTimeToMinProfit(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .mapToLong(TradingPair::getMinutesToMinProfitPercent)
                .filter(minutes -> minutes > 0)
                .average();

        if (average.isPresent()) {
            return String.format("%.1f мин", average.getAsDouble());
        }
        return "N/A";
    }

    private String calculateAverageTimeToMaxProfit(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .mapToLong(TradingPair::getMinutesToMaxProfitPercent)
                .filter(minutes -> minutes > 0)
                .average();

        if (average.isPresent()) {
            return String.format("%.1f мин", average.getAsDouble());
        }
        return "N/A";
    }

    private BigDecimal calculateAverageZScoreEntry(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(TradingPair::getZScoreEntry)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageZScoreCurrent(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(TradingPair::getZScoreCurrent)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageZScoreMax(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(TradingPair::getMaxZ)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageZScoreMin(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(TradingPair::getMinZ)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageCorrelationEntry(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(TradingPair::getCorrelationEntry)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageCorrelationCurrent(List<TradingPair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(TradingPair::getCorrelationCurrent)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }
}
