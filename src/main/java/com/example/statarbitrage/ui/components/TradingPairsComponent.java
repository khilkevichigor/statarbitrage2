package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.NumberFormatter;
import com.example.statarbitrage.core.processors.StartNewTradeProcessor;
import com.example.statarbitrage.core.processors.UpdateTradeProcessor;
import com.example.statarbitrage.core.services.PairDataService;
import com.example.statarbitrage.core.services.TradeLogService;
import com.example.statarbitrage.trading.services.TradingProviderFactory;
import com.example.statarbitrage.ui.dto.StartNewTradeRequest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@SpringComponent
@UIScope
public class TradingPairsComponent extends VerticalLayout {

    private final PairDataService pairDataService;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final TradeLogService tradeLogService;
    private final ZScoreChartDialog zScoreChartDialog;

    private final Grid<PairData> selectedPairsGrid;
    private final Grid<PairData> tradingPairsGrid;
    private final Grid<PairData> closedPairsGrid;
    private final VerticalLayout unrealizedProfitLayout;

    private Consumer<Void> uiUpdateCallback;
    private TradingProviderFactory tradingProviderFactory;

    public TradingPairsComponent(PairDataService pairDataService,
                                 StartNewTradeProcessor startNewTradeProcessor,
                                 UpdateTradeProcessor updateTradeProcessor,
                                 TradeLogService tradeLogService,
                                 ZScoreChartDialog zScoreChartDialog, TradingProviderFactory tradingProviderFactory) {
        this.pairDataService = pairDataService;
        this.startNewTradeProcessor = startNewTradeProcessor;
        this.updateTradeProcessor = updateTradeProcessor;
        this.tradeLogService = tradeLogService;
        this.zScoreChartDialog = zScoreChartDialog;
        this.tradingProviderFactory = tradingProviderFactory;

        this.selectedPairsGrid = new Grid<>(PairData.class, false);
        this.tradingPairsGrid = new Grid<>(PairData.class, false);
        this.closedPairsGrid = new Grid<>(PairData.class, false);
        this.unrealizedProfitLayout = new VerticalLayout();

        initializeComponent();
        setupGrids();
    }

    private void initializeComponent() {
        setSpacing(true);
        setPadding(false);

        add(
                selectedPairsGrid,
                tradingPairsGrid,
                unrealizedProfitLayout,
                closedPairsGrid
        );
    }

    private void setupGrids() {
        setupSelectedPairsGrid();
        setupTradingPairsGrid();
        setupClosedPairsGrid();
        setupCommonGridProperties();
    }

    private void setupSelectedPairsGrid() {
        selectedPairsGrid.addColumn(PairData::getLongTicker)
                .setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(PairData::getShortTicker)
                .setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent()))
                .setHeader("Z-скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent()))
                .setHeader("PValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent()))
                .setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent()))
                .setHeader("Корр.").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(new ComponentRenderer<>(this::createSelectedPairsActionButtons))
                .setHeader("Действие");
    }

    private void setupTradingPairsGrid() {
        tradingPairsGrid.addColumn(PairData::getLongTicker)
                .setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(PairData::getShortTicker)
                .setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(PairData::getProfitChanges)
                .setHeader("Профит (%)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> p.getLongChanges().setScale(2, RoundingMode.HALF_UP))
                .setHeader("Long (%)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> p.getShortChanges().setScale(2, RoundingMode.HALF_UP))
                .setHeader("Short (%)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getTimeInMinutesSinceEntryToMin()))
                .setHeader("Min Long Time (min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getTimeInMinutesSinceEntryToMax()))
                .setHeader("Max Short Time (min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry()))
                .setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent()))
                .setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry()))
                .setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent()))
                .setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry()))
                .setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent()))
                .setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry()))
                .setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent()))
                .setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createTradingPairsActionButtons))
                .setHeader("Действие");
    }

    private void setupClosedPairsGrid() {
        closedPairsGrid.addColumn(PairData::getLongTicker)
                .setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(PairData::getShortTicker)
                .setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(PairData::getProfitChanges)
                .setHeader("Профит (%)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> p.getLongChanges().setScale(2, RoundingMode.HALF_UP))
                .setHeader("Long (%)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> p.getShortChanges().setScale(2, RoundingMode.HALF_UP))
                .setHeader("Short (%)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getTimeInMinutesSinceEntryToMin()))
                .setHeader("Min Long Time (min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getTimeInMinutesSinceEntryToMax()))
                .setHeader("Max Short Time (min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry()))
                .setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent()))
                .setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry()))
                .setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent()))
                .setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry()))
                .setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent()))
                .setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry()))
                .setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent()))
                .setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(PairData::getExitReason)
                .setHeader("Причина выхода").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(new ComponentRenderer<>(this::createClosedPairsActionButtons))
                .setHeader("Чарт");
    }

    private void setupCommonGridProperties() {
        selectedPairsGrid.setHeight("300px");
        selectedPairsGrid.setWidthFull();
        tradingPairsGrid.setHeight("300px");
        tradingPairsGrid.setWidthFull();
        closedPairsGrid.setHeight("300px");
        closedPairsGrid.setWidthFull();
    }

    private Button createStartTradingButton(PairData pairData) {
        Button actionButton = new Button("Торговать", event -> {
            try {
                // Ручной запуск - НЕ проверяем автотрейдинг
                PairData newPairData = startNewTradeProcessor.startNewTrade(StartNewTradeRequest.builder()
                        .pairData(pairData)
                        .checkAutoTrading(false)
                        .build());
                if (newPairData != null) {
                    Notification.show(String.format(
                            "Статус пары %s/%s изменен на %s",
                            pairData.getLongTicker(), pairData.getShortTicker(), TradeStatus.TRADING
                    ));
                } else {
                    Notification.show(String.format(
                            "Пропускаем новый трейд для пары %s/%s",
                            pairData.getLongTicker(), pairData.getShortTicker()
                    ));
                }

                notifyUIUpdate();
            } catch (Exception e) {
                log.error("Error starting trade for pair: {}/{}", pairData.getLongTicker(), pairData.getShortTicker(), e);
                Notification.show("Ошибка при открытии торговли: " + e.getMessage());
            }
        });

        actionButton.getStyle().set("color", "green");
        return actionButton;
    }

    private Button createStopTradingButton(PairData pairData) {
        Button actionButton = new Button("Закрыть", event -> {
            try {
                PairData updatedPairData = updateTradeProcessor.updateTrade(pairData, true);
                Notification.show(String.format(
                        "Статус пары %s/%s изменен на %s",
                        updatedPairData.getLongTicker(), updatedPairData.getShortTicker(), updatedPairData.getStatus()
                ));
                notifyUIUpdate();
            } catch (Exception e) {
                log.error("Error closing trade for pairData: {}/{}", pairData.getLongTicker(), pairData.getShortTicker(), e);
                Notification.show("Ошибка при закрытии торговли: " + e.getMessage());
            }
        });

        actionButton.getStyle().set("color", "red");
        return actionButton;
    }

    public void updateSelectedPairs() {
        try {
            List<PairData> pairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.SELECTED);
            selectedPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating selected pairs", e);
        }
    }

    public void updateTradingPairs() {
        try {
            List<PairData> pairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            tradingPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating trading pairs", e);
        }
    }

    public void updateClosedPairs() {
        try {
            List<PairData> pairs = pairDataService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.CLOSED);
            closedPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating closed pairs", e);
        }
    }

    public void updateUnrealizedProfit() {
        try {
            unrealizedProfitLayout.removeAll();
            BigDecimal unrealizedProfit = pairDataService.getUnrealizedProfitTotal();
            String formatted = unrealizedProfit.setScale(2, RoundingMode.HALF_UP) + " %";

            H2 profitInfo = new H2("💰 Нереализованный профит: " + formatted);
            unrealizedProfitLayout.add(profitInfo);
        } catch (Exception e) {
            log.error("Error updating unrealized profit", e);
        }
    }

    public void updateAllData() {
        updateSelectedPairs();
        updateTradingPairs();
        updateClosedPairs();
        updateUnrealizedProfit();
    }

    public void setSelectedPairs(List<PairData> pairs) {
        selectedPairsGrid.setItems(pairs);
    }

    public void setUiUpdateCallback(Consumer<Void> callback) {
        this.uiUpdateCallback = callback;
    }

    private void notifyUIUpdate() {
        if (uiUpdateCallback != null) {
            uiUpdateCallback.accept(null);
        }
    }

    /**
     * Создает кнопки действий для Selected Pairs Grid
     */
    private HorizontalLayout createSelectedPairsActionButtons(PairData pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);

        // Кнопка Chart
        Button chartButton = createChartButton(pair);

        // Кнопка Торговать
        Button tradeButton = createStartTradingButton(pair);

        buttonsLayout.add(chartButton, tradeButton);
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Trading Pairs Grid
     */
    private HorizontalLayout createTradingPairsActionButtons(PairData pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);

        // Кнопка Chart
        Button chartButton = createChartButton(pair);

        // Кнопка Закрыть
        Button closeButton = createStopTradingButton(pair);

        buttonsLayout.add(chartButton, closeButton);
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Closed Pairs Grid (только Chart)
     */
    private Button createClosedPairsActionButtons(PairData pair) {
        return createChartButton(pair);
    }

    /**
     * Создает кнопку Chart для отображения Z-Score графика
     */
    private Button createChartButton(PairData pair) {
        Button chartButton = new Button(VaadinIcon.LINE_CHART.create());
        chartButton.getElement().setAttribute("title", "Показать Z-Score график");
        chartButton.getStyle().set("color", "#2196F3");

        chartButton.addClickListener(event -> {
            try {
                log.info("📊 Открываем Z-Score чарт для пары: {} / {}",
                        pair.getLongTicker(), pair.getShortTicker());
                zScoreChartDialog.showChart(pair);
            } catch (Exception e) {
                log.error("❌ Ошибка при показе чарта для пары: {} / {}",
                        pair.getLongTicker(), pair.getShortTicker(), e);
                Notification.show("Ошибка при загрузке чарта: " + e.getMessage());
            }
        });

        return chartButton;
    }

    // Методы для фильтрации отображения различных типов пар

    public void showOnlySelectedPairs() {
        selectedPairsGrid.setVisible(true);
        tradingPairsGrid.setVisible(false);
        closedPairsGrid.setVisible(false);
        unrealizedProfitLayout.setVisible(false);
    }

    public void showOnlyTradingPairs() {
        selectedPairsGrid.setVisible(false);
        tradingPairsGrid.setVisible(true);
        closedPairsGrid.setVisible(false);
        unrealizedProfitLayout.setVisible(true);
    }

    public void showOnlyClosedPairs() {
        selectedPairsGrid.setVisible(false);
        tradingPairsGrid.setVisible(false);
        closedPairsGrid.setVisible(true);
        unrealizedProfitLayout.setVisible(false);
    }

    public void showAllPairs() {
        selectedPairsGrid.setVisible(true);
        tradingPairsGrid.setVisible(true);
        closedPairsGrid.setVisible(true);
        unrealizedProfitLayout.setVisible(true);
    }
}