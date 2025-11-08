package com.example.core.services;

import com.example.core.repositories.TradeHistoryRepository;
import com.example.shared.dto.PairAggregatedStatisticsDto;
import com.example.shared.dto.TradePairsStatisticsDto;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
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
    private final PairService pairService;
    private final TradeHistoryService tradeHistoryService;

    public TradePairsStatisticsDto collectStatistics() {
        BigDecimal unrealizedProfitUSDTToday = pairService.findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus.TRADING).stream()
                .map(Pair::getProfitUSDTChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedProfitUSDTTotal = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).stream()
                .map(Pair::getProfitUSDTChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedProfitPercentToday = pairService.findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus.TRADING).stream()
                .map(Pair::getProfitPercentChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unrealizedProfitPercentTotal = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING).stream()
                .map(Pair::getProfitPercentChanges)
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
            List<Pair> closedPairs = pairService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.CLOSED);

            Map<String, List<Pair>> pairGroups = closedPairs.stream()
                    .collect(Collectors.groupingBy(Pair::getPairName));

            return pairGroups.entrySet().stream()
                    .map(entry -> {
                        String pairName = entry.getKey();
                        List<Pair> pairs = entry.getValue();

                        return PairAggregatedStatisticsDto.builder()
                                .pairName(pairName)
                                .totalTrades(pairs.size())
                                .totalProfitUSDT(calculateTotalProfit(pairs))
                                .averageProfitPercent(calculateAverageProfitPercent(pairs))
                                .averageTradeDuration(calculateAverageTradeDurationMinutes(pairs))
                                .totalAveragingCount(calculateTotalAveragingCount(pairs))
                                .averageTimeToMinProfit(calculateAverageTimeToMinProfitMinutes(pairs))
                                .averageTimeToMaxProfit(calculateAverageTimeToMaxProfitMinutes(pairs))
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

    private BigDecimal calculateTotalProfit(List<Pair> pairs) {
        return pairs.stream()
                .map(Pair::getProfitUSDTChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateAverageProfitPercent(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(Pair::getProfitPercentChanges)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ? BigDecimal.valueOf(average.getAsDouble()) : BigDecimal.ZERO;
    }

    private String calculateAverageTradeDuration(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .filter(p -> p.getEntryTime() != null && p.getUpdatedTime() != null)
                .mapToLong(p -> p.getUpdatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() -
                        p.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                .average();

        if (average.isPresent()) {
            return TimeFormatterUtil.formatDurationFromMillis((long) average.getAsDouble());
        }
        return "N/A";
    }

    private long calculateAverageTradeDurationMinutes(List<Pair> pairs) {
        return (long) pairs.stream()
                .filter(p -> p.getEntryTime() != null && p.getUpdatedTime() != null)
                .mapToLong(p -> p.getUpdatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() -
                        p.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) // миллисекунды
                .average()
                .orElse(0.0) / 1000 / 60;
    }

    private long calculateTotalAveragingCount(List<Pair> pairs) {
        return pairs.stream()
                .filter(p -> p.getAveragingCount() != null)
                .mapToInt(Pair::getAveragingCount)
                .sum();
    }

    private long calculateAverageTimeToMinProfitMinutes(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .mapToLong(Pair::getMinutesToMinProfitPercent)
                .filter(minutes -> minutes > 0)
                .average();

        return average.isPresent() ? Math.round(average.getAsDouble()) : -1L;
    }

    private long calculateAverageTimeToMaxProfitMinutes(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .mapToLong(Pair::getMinutesToMaxProfitPercent)
                .filter(minutes -> minutes > 0)
                .average();

        return average.isPresent() ? Math.round(average.getAsDouble()) : -1L;
    }

    private BigDecimal calculateAverageZScoreEntry(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(Pair::getZScoreEntry)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageZScoreCurrent(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(Pair::getZScoreCurrent)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageZScoreMax(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(Pair::getMaxZ)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageZScoreMin(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(Pair::getMinZ)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageCorrelationEntry(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(Pair::getCorrelationEntry)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }

    private BigDecimal calculateAverageCorrelationCurrent(List<Pair> pairs) {
        OptionalDouble average = pairs.stream()
                .map(Pair::getCorrelationCurrent)
                .filter(Objects::nonNull)
                .mapToDouble(value -> value.doubleValue())
                .average();
        return average.isPresent() ?
                BigDecimal.valueOf(average.getAsDouble()).setScale(4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
    }
}
