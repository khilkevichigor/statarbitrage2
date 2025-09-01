package com.example.core.ui.components;

import com.example.core.services.StatisticsService;
import com.example.shared.dto.PairAggregatedStatisticsDto;
import com.example.shared.dto.TradePairsStatisticsDto;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@SpringComponent
@UIScope
public class StatisticsComponent extends VerticalLayout {

    private final StatisticsService statisticsService;
    private final Grid<StatisticRow> statisticsGrid;
    private final Grid<PairAggregatedStatisticsDto> pairStatsGrid;

    public StatisticsComponent(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
        this.statisticsGrid = new Grid<>();
        this.pairStatsGrid = new Grid<>(PairAggregatedStatisticsDto.class, false);

        initializeComponent();
        setupGrid();
        setupPairStatsGrid();
    }

    private void initializeComponent() {
        setSpacing(false);
        setPadding(false);

        H2 title = new H2("📊 Статистика трейдов");
        H2 pairStatsTitle = new H2("📈 Статистика по парам (закрытые)");
        add(title, statisticsGrid, pairStatsTitle, pairStatsGrid);
    }

    private void setupGrid() {
        statisticsGrid.setAllRowsVisible(true);
        statisticsGrid.addColumn(StatisticRow::name).setHeader("Показатель");
        statisticsGrid.addColumn(StatisticRow::today).setHeader("Сегодня");
        statisticsGrid.addColumn(StatisticRow::total).setHeader("Всего");
    }

    private void setupPairStatsGrid() {
        pairStatsGrid.setHeight("400px");
        pairStatsGrid.setWidthFull();

        pairStatsGrid.addColumn(PairAggregatedStatisticsDto::getPairName)
                .setHeader("Пара").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(PairAggregatedStatisticsDto::getTotalTrades)
                .setHeader("Кол-во трейдов").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(dto -> formatBigDecimal(dto.getTotalProfitUSDT()) + "$")
                .setHeader("Суммарный профит USDT").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(dto -> formatBigDecimal(dto.getAverageProfitPercent()) + "%")
                .setHeader("Средний профит %").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(PairAggregatedStatisticsDto::getAverageTradeDuration)
                .setHeader("Средняя длительность").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(PairAggregatedStatisticsDto::getTotalAveragingCount)
                .setHeader("Всего усреднений").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(PairAggregatedStatisticsDto::getAverageTimeToMinProfit)
                .setHeader("Ср. время до Min профита").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(PairAggregatedStatisticsDto::getAverageTimeToMaxProfit)
                .setHeader("Ср. время до Max профита").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(dto -> formatBigDecimal(dto.getAverageZScoreEntry()))
                .setHeader("Ср. Z-Score Entry").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(dto -> formatBigDecimal(dto.getAverageZScoreCurrent()))
                .setHeader("Ср. Z-Score Current").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(dto -> formatBigDecimal(dto.getAverageZScoreMax()))
                .setHeader("Ср. Z-Score Max").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(dto -> formatBigDecimal(dto.getAverageZScoreMin()))
                .setHeader("Ср. Z-Score Min").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(dto -> formatBigDecimal(dto.getAverageCorrelationEntry()))
                .setHeader("Ср. Correlation Entry").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        pairStatsGrid.addColumn(dto -> formatBigDecimal(dto.getAverageCorrelationCurrent()))
                .setHeader("Ср. Correlation Current").setSortable(true).setAutoWidth(true).setFlexGrow(0);
    }

    public void updateStatistics() {
        try {
            TradePairsStatisticsDto stats = statisticsService.collectStatistics();

            List<StatisticRow> rows = List.of(
                    new StatisticRow("Пар с ошибками", stats.getTradePairsWithErrorToday(), stats.getTradePairsWithErrorTotal()),
                    new StatisticRow("Отторгованных пар", stats.getTradePairsToday(), stats.getTradePairsTotal()),

                    new StatisticRow("Avg Профит с пары", getAvgProfitToday(stats), getAvgProfitTotal(stats)),
                    new StatisticRow("Сумма Профита", getSumProfitToday(stats), getSumProfitTotal(stats)),

                    new StatisticRow("Выход: STOP", stats.getExitByStopToday(), stats.getExitByStopTotal()),
                    new StatisticRow("Выход: TAKE", stats.getExitByTakeToday(), stats.getExitByTakeTotal()),
                    new StatisticRow("Выход: Z MIN", stats.getExitByZMinToday(), stats.getExitByZMinTotal()),
                    new StatisticRow("Выход: Z MAX", stats.getExitByZMaxToday(), stats.getExitByZMaxTotal()),
                    new StatisticRow("Выход: TIME", stats.getExitByTimeToday(), stats.getExitByTimeTotal()),
                    new StatisticRow("Выход: BREAKEVEN", stats.getExitByBreakevenToday(), stats.getExitByBreakevenTotal()),
                    new StatisticRow("Выход: Z<0 МИН ПРОФИТ", stats.getExitByNegativeZMinProfitToday(), stats.getExitByNegativeZMinProfitTotal()),
                    new StatisticRow("Выход: MANUALLY", stats.getExitByManuallyToday(), stats.getExitByManuallyTotal()),

                    new StatisticRow("Профит нереализованный", getSumProfitUnrealizedToday(stats), getSumProfitUnrealizedTotal(stats)),
                    new StatisticRow("Профит реализованный", getSumProfitRealizedToday(stats), getSumProfitRealizedTotal(stats)),
                    new StatisticRow("Профит общий", getSumProfitCombinedToday(stats), getSumProfitCombinedTotal(stats))
            );

            statisticsGrid.setItems(rows);

            List<PairAggregatedStatisticsDto> pairStats = statisticsService.getClosedPairsAggregatedStatistics();
            pairStatsGrid.setItems(pairStats);
        } catch (Exception e) {
            log.error("❌ Ошибка обновления статистики", e);
        }
    }

    private String getAvgProfitToday(TradePairsStatisticsDto stats) {
        BigDecimal avgUsd = stats.getAvgProfitUSDTToday() != null ? stats.getAvgProfitUSDTToday() : BigDecimal.ZERO;
        BigDecimal avgPercent = stats.getAvgProfitPercentToday() != null ? stats.getAvgProfitPercentToday() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                avgUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                avgPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getAvgProfitTotal(TradePairsStatisticsDto stats) {
        BigDecimal avgUsd = stats.getAvgProfitUSDTTotal() != null ? stats.getAvgProfitUSDTTotal() : BigDecimal.ZERO;
        BigDecimal avgPercent = stats.getAvgProfitPercentTotal() != null ? stats.getAvgProfitPercentTotal() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                avgUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                avgPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitToday(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitUSDTToday() != null ? stats.getSumProfitUSDTToday() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitPercentToday() != null ? stats.getSumProfitPercentToday() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitTotal(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitUSDTTotal() != null ? stats.getSumProfitUSDTTotal() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitPercentTotal() != null ? stats.getSumProfitPercentTotal() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitUnrealizedToday(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitUnrealizedUSDTToday() != null ? stats.getSumProfitUnrealizedUSDTToday() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitUnrealizedPercentToday() != null ? stats.getSumProfitUnrealizedPercentToday() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitUnrealizedTotal(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitUnrealizedUSDTTotal() != null ? stats.getSumProfitUnrealizedUSDTTotal() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitUnrealizedPercentTotal() != null ? stats.getSumProfitUnrealizedPercentTotal() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitRealizedToday(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitRealizedUSDTToday() != null ? stats.getSumProfitRealizedUSDTToday() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitRealizedPercentToday() != null ? stats.getSumProfitRealizedPercentToday() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitRealizedTotal(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitRealizedUSDTTotal() != null ? stats.getSumProfitRealizedUSDTTotal() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitRealizedPercentTotal() != null ? stats.getSumProfitRealizedPercentTotal() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitCombinedToday(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitCombinedUSDTToday() != null ? stats.getSumProfitCombinedUSDTToday() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitCombinedPercentToday() != null ? stats.getSumProfitCombinedPercentToday() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitCombinedTotal(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitCombinedUSDTTotal() != null ? stats.getSumProfitCombinedUSDTTotal() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitCombinedPercentTotal() != null ? stats.getSumProfitCombinedPercentTotal() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String formatBigDecimal(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public record StatisticRow(String name, Object today, Object total) {
    }
}