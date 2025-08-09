package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.common.dto.TradePairsStatisticsDto;
import com.example.statarbitrage.core.services.StatisticsService;
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

    public StatisticsComponent(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
        this.statisticsGrid = new Grid<>();

        initializeComponent();
        setupGrid();
    }

    private void initializeComponent() {
        setSpacing(false);
        setPadding(false);

        H2 title = new H2("📊 Статистика трейдов");
        add(title, statisticsGrid);
    }

    private void setupGrid() {
        statisticsGrid.setAllRowsVisible(true);
        statisticsGrid.addColumn(StatisticRow::name).setHeader("Показатель");
        statisticsGrid.addColumn(StatisticRow::today).setHeader("Сегодня");
        statisticsGrid.addColumn(StatisticRow::total).setHeader("Всего");
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
                    new StatisticRow("Выход: MANUALLY", stats.getExitByManuallyToday(), stats.getExitByManuallyTotal()),

                    new StatisticRow("Профит нереализованный", "", getSumProfitUnrealizedTotal(stats)),
                    new StatisticRow("Профит реализованный", "", getSumProfitRealizedTotal(stats)),
                    new StatisticRow("Профит общий", "", getSumProfitCombinedTotal(stats))
            );

            statisticsGrid.setItems(rows);
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

    private String getSumProfitUnrealizedTotal(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitUnrealizedUSDT() != null ? stats.getSumProfitUnrealizedUSDT() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitUnrealizedPercent() != null ? stats.getSumProfitUnrealizedPercent() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitRealizedTotal(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitRealizedUSDT() != null ? stats.getSumProfitRealizedUSDT() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitRealizedPercent() != null ? stats.getSumProfitRealizedPercent() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private String getSumProfitCombinedTotal(TradePairsStatisticsDto stats) {
        BigDecimal sumUsd = stats.getSumProfitCombinedUSDT() != null ? stats.getSumProfitCombinedUSDT() : BigDecimal.ZERO;
        BigDecimal sumPercent = stats.getSumProfitCombinedPercent() != null ? stats.getSumProfitCombinedPercent() : BigDecimal.ZERO;

        return String.format("%s$/%s%%",
                sumUsd.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                sumPercent.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    public record StatisticRow(String name, Object today, Object total) {
    }
}