package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.common.dto.TradePairsStatisticsDto;
import com.example.statarbitrage.common.utils.NumberFormatter;
import com.example.statarbitrage.core.services.StatisticsService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;

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
                    new StatisticRow("Торгуемых пар", stats.getTradePairsToday(), stats.getTradePairsTotal()),
                    new StatisticRow("Avg Профит (USDT)", NumberFormatter.format(stats.getAvgProfitPercentToday()), NumberFormatter.format(stats.getAvgProfitPercentTotal())),
                    new StatisticRow("Avg Профит (%)", NumberFormatter.format(stats.getAvgProfitPercentToday()), NumberFormatter.format(stats.getAvgProfitPercentTotal())),
                    new StatisticRow("Сумма Профита (USDT)", NumberFormatter.format(stats.getSumProfitUSDTToday()), NumberFormatter.format(stats.getSumProfitUSDTTotal())),
                    new StatisticRow("Сумма Профита (%)", NumberFormatter.format(stats.getSumProfitPercentToday()), NumberFormatter.format(stats.getSumProfitPercentTotal())),
                    new StatisticRow("Выход: STOP", stats.getExitByStopToday(), stats.getExitByStopTotal()),
                    new StatisticRow("Выход: TAKE", stats.getExitByTakeToday(), stats.getExitByTakeTotal()),
                    new StatisticRow("Выход: Z MIN", stats.getExitByZMinToday(), stats.getExitByZMinTotal()),
                    new StatisticRow("Выход: Z MAX", stats.getExitByZMaxToday(), stats.getExitByZMaxTotal()),
                    new StatisticRow("Выход: TIME", stats.getExitByTimeToday(), stats.getExitByTimeTotal()),
                    new StatisticRow("Выход: MANUALLY", stats.getExitByManuallyToday(), stats.getExitByManuallyTotal()),

                    new StatisticRow("Профит нереализованный (USDT)", "", NumberFormatter.format(stats.getSumProfitUnrealizedUSDT())),
                    new StatisticRow("Профит нереализованный (%)", "", NumberFormatter.format(stats.getSumProfitUnrealizedPercent())),

                    new StatisticRow("Профит реализованный (USDT)", "", NumberFormatter.format(stats.getSumProfitRealizedPercent())),
                    new StatisticRow("Профит реализованный (%)", "", NumberFormatter.format(stats.getSumProfitRealizedPercent())),

                    new StatisticRow("Профит общий (USDT)", "", NumberFormatter.format(stats.getSumProfitCombinedUSDT())),
                    new StatisticRow("Профит общий (%)", "", NumberFormatter.format(stats.getSumProfitCombinedPercent()))
            );

            statisticsGrid.setItems(rows);
        } catch (Exception e) {
            log.error("❌ Ошибка обновления статистики", e);
        }
    }

    public record StatisticRow(String name, Object today, Object total) {
    }
}