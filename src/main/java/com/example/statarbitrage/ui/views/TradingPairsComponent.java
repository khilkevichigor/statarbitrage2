package com.example.statarbitrage.ui.views;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.core.processors.StartNewTradeProcessor;
import com.example.statarbitrage.core.processors.UpdateTradeProcessor;
import com.example.statarbitrage.core.services.PairDataService;
import com.example.statarbitrage.core.services.TradeLogService;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.NumberFormatter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Consumer;

import static com.example.statarbitrage.common.constant.Constants.EXIT_REASON_MANUALLY;

@Slf4j
@SpringComponent
@UIScope
public class TradingPairsComponent extends VerticalLayout {

    private final PairDataService pairDataService;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final TradeLogService tradeLogService;

    private final Grid<PairData> selectedPairsGrid;
    private final Grid<PairData> tradingPairsGrid;
    private final Grid<PairData> closedPairsGrid;
    private final VerticalLayout unrealizedProfitLayout;

    private Consumer<Void> uiUpdateCallback;

    public TradingPairsComponent(PairDataService pairDataService,
                                 StartNewTradeProcessor startNewTradeProcessor,
                                 UpdateTradeProcessor updateTradeProcessor,
                                 TradeLogService tradeLogService) {
        this.pairDataService = pairDataService;
        this.startNewTradeProcessor = startNewTradeProcessor;
        this.updateTradeProcessor = updateTradeProcessor;
        this.tradeLogService = tradeLogService;

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
                new H2("Отобранные пары (SELECTED)"),
                selectedPairsGrid,
                new H2("Торгуемые пары (TRADING)"),
                tradingPairsGrid,
                unrealizedProfitLayout,
                new H2("Закрытые пары (CLOSED)"),
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

        selectedPairsGrid.addColumn(new ComponentRenderer<>(this::createStartTradingButton))
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

        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createStopTradingButton))
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
    }

    private void setupCommonGridProperties() {
        selectedPairsGrid.setHeight("300px");
        selectedPairsGrid.setWidthFull();
        tradingPairsGrid.setHeight("300px");
        tradingPairsGrid.setWidthFull();
        closedPairsGrid.setHeight("300px");
        closedPairsGrid.setWidthFull();
    }

    private Button createStartTradingButton(PairData pair) {
        Button actionButton = new Button("Торговать", event -> {
            try {
                startNewTradeProcessor.startNewTrade(pair);
                Notification.show(String.format(
                        "Статус пары %s/%s изменен на %s",
                        pair.getLongTicker(), pair.getShortTicker(), TradeStatus.TRADING
                ));
                notifyUIUpdate();
            } catch (Exception e) {
                log.error("Error starting trade for pair: {}/{}", pair.getLongTicker(), pair.getShortTicker(), e);
                Notification.show("Ошибка при открытии торговли: " + e.getMessage());
            }
        });

        actionButton.getStyle().set("color", "green");
        return actionButton;
    }

    private Button createStopTradingButton(PairData pair) {
        Button actionButton = new Button("Закрыть", event -> {
            try {
                updateTradeProcessor.updateTrade(pair);

                pair.setStatus(TradeStatus.CLOSED);
                pair.setExitReason(EXIT_REASON_MANUALLY);
                pairDataService.saveToDb(pair);
                tradeLogService.saveFromPairData(pair);

                Notification.show(String.format(
                        "Статус пары %s/%s изменен на %s",
                        pair.getLongTicker(), pair.getShortTicker(), TradeStatus.CLOSED
                ));
                notifyUIUpdate();
            } catch (Exception e) {
                log.error("Error closing trade for pair: {}/{}", pair.getLongTicker(), pair.getShortTicker(), e);
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
}