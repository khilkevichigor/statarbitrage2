package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.NumberFormatter;
import com.example.statarbitrage.core.processors.StartNewTradeProcessor;
import com.example.statarbitrage.core.processors.UpdateTradeProcessor;
import com.example.statarbitrage.core.services.PairDataService;
import com.example.statarbitrage.core.services.PixelSpreadService;
import com.example.statarbitrage.formatters.TimeFormatterUtil;
import com.example.statarbitrage.ui.dto.StartNewTradeRequest;
import com.example.statarbitrage.ui.dto.UpdateTradeRequest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import java.util.List;
import java.util.function.Consumer;

import static com.example.statarbitrage.common.utils.BigDecimalUtil.safeScale;

@Slf4j
@SpringComponent
@UIScope
public class TradingPairsComponent extends VerticalLayout {

    private final PairDataService pairDataService;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final ZScoreChartDialog zScoreChartDialog;
    private final PixelSpreadService pixelSpreadService;

    private final Grid<PairData> selectedPairsGrid;
    private final Grid<PairData> tradingPairsGrid;
    private final Grid<PairData> closedPairsGrid;
    private final Grid<PairData> errorPairsGrid;
    private final Grid<PairData> observedPairsGrid;
    private final VerticalLayout unrealizedProfitLayout;

    private Consumer<Void> uiUpdateCallback;

    public TradingPairsComponent(
            PairDataService pairDataService,
            StartNewTradeProcessor startNewTradeProcessor,
            UpdateTradeProcessor updateTradeProcessor,
            ZScoreChartDialog zScoreChartDialog,
            PixelSpreadService pixelSpreadService
    ) {
        this.pairDataService = pairDataService;
        this.startNewTradeProcessor = startNewTradeProcessor;
        this.updateTradeProcessor = updateTradeProcessor;
        this.zScoreChartDialog = zScoreChartDialog;
        this.pixelSpreadService = pixelSpreadService;

        this.selectedPairsGrid = new Grid<>(PairData.class, false);
        this.tradingPairsGrid = new Grid<>(PairData.class, false);
        this.closedPairsGrid = new Grid<>(PairData.class, false);
        this.errorPairsGrid = new Grid<>(PairData.class, false);
        this.observedPairsGrid = new Grid<>(PairData.class, false);
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
                closedPairsGrid,
                errorPairsGrid,
                observedPairsGrid
        );
    }

    private void setupGrids() {
        setupSelectedPairsGrid();
        setupTradingPairsGrid();
        setupClosedPairsGrid();
        setupErrorPairsGrid();
        setupObservedPairsGrid();
        setupCommonGridProperties();
    }

    private void setupSelectedPairsGrid() {
        selectedPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("PValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Корр.").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getTimestamp())).setHeader("Дата/время").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(new ComponentRenderer<>(this::createSelectedPairsChartActionButtons)).setHeader("Чарт");
        selectedPairsGrid.addColumn(new ComponentRenderer<>(this::createSelectedPairsStartTradingActionButtons)).setHeader("Торговать");
    }

    private void setupTradingPairsGrid() {
        tradingPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createTradingPairsChartActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        tradingPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(PairData::getFormattedProfitCommon).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> TimeFormatterUtil.formatDurationFromMillis(p.getUpdatedTime() - p.getEntryTime())).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry())).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(PairData::getFormattedTimeToMinProfit).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(PairData::getFormattedTimeToMaxProfit).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(PairData::getFormattedProfitLong).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(PairData::getFormattedProfitShort).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry())).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry())).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry())).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getEntryTime())).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createCloseAtBreakevenCheckbox)).setHeader("Закрыть в БУ").setSortable(true).setAutoWidth(true);
        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createTradingPairsCloseActionButtons)).setHeader("Действие").setSortable(true).setAutoWidth(true);
    }

    private void setupClosedPairsGrid() {
        closedPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(PairData::getExitReason).setHeader("Причина выхода").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(new ComponentRenderer<>(this::createClosedPairsActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        closedPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(PairData::getFormattedProfitCommon).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> safeScale(p.getPortfolioAfterTradeUSDT(), 2) + "$").setHeader("Баланс ПОСЛЕ").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> TimeFormatterUtil.formatDurationFromMillis(p.getUpdatedTime() - p.getEntryTime())).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry())).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(PairData::getFormattedTimeToMinProfit).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(PairData::getFormattedTimeToMaxProfit).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(PairData::getFormattedProfitLong).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(PairData::getFormattedProfitShort).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry())).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry())).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry())).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getEntryTime())).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getUpdatedTime())).setHeader("Конец трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
    }

    private void setupErrorPairsGrid() {
        errorPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(PairData::getErrorDescription).setHeader("Ошибка").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(new ComponentRenderer<>(this::createErrorPairsActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        errorPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(PairData::getFormattedProfitCommon).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> safeScale(p.getPortfolioAfterTradeUSDT(), 2) + "$").setHeader("Баланс ПОСЛЕ").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry())).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(PairData::getFormattedTimeToMinProfit).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(PairData::getFormattedTimeToMaxProfit).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(PairData::getFormattedProfitLong).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(PairData::getFormattedProfitShort).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry())).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry())).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry())).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

//        errorPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getEntryTime())).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getUpdatedTime())).setHeader("Дата").setSortable(true).setAutoWidth(true).setFlexGrow(0);
//        errorPairsGrid.addColumn(p -> TimeFormatterUtil.formatDurationFromMillis(p.getUpdatedTime() - p.getEntryTime())).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

//        errorPairsGrid.addColumn(PairData::getExitReason).setHeader("Причина выхода").setSortable(true).setAutoWidth(true).setFlexGrow(0);
    }

    private void setupObservedPairsGrid() {
        observedPairsGrid.addColumn(PairData::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(PairData::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        observedPairsGrid.addColumn(new ComponentRenderer<>(this::createChartButton)).setHeader("Чарт");

        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("PValue").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Corr").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        observedPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getUpdatedTime())).setHeader("Дата/время").setSortable(true).setAutoWidth(true).setFlexGrow(0);
    }

    private void setupCommonGridProperties() {
        selectedPairsGrid.setHeight("300px");
        selectedPairsGrid.setWidthFull();
        tradingPairsGrid.setHeight("300px");
        tradingPairsGrid.setWidthFull();
        closedPairsGrid.setHeight("300px");
        closedPairsGrid.setWidthFull();
        errorPairsGrid.setHeight("300px");
        errorPairsGrid.setWidthFull();
        observedPairsGrid.setHeight("300px");
        observedPairsGrid.setWidthFull();
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
                            "Статус пары %s изменен на %s",
                            pairData.getPairName(), TradeStatus.TRADING
                    ));
                } else {
                    Notification.show(String.format(
                            "Пропускаем новый трейд для пары %s",
                            pairData.getPairName()
                    ));
                }

                notifyUIUpdate();
            } catch (Exception e) {
                log.error("❌ Ошибка начала торговли для пары: {}", pairData.getPairName(), e);
                Notification.show("Ошибка при открытии торговли: " + e.getMessage());
            }
        });

        actionButton.getStyle().set("color", "green");
        return actionButton;
    }

    private Button createStopTradingButton(PairData pairData) {
        Button actionButton = new Button("Закрыть", event -> {
            try {
                PairData updatedPairData = updateTradeProcessor.updateTrade(UpdateTradeRequest.builder()
                        .pairData(pairData)
                        .closeManually(true)
                        .build());
                Notification.show(String.format(
                        "Статус пары %s изменен на %s",
                        updatedPairData.getPairName(), updatedPairData.getStatus()
                ));
                notifyUIUpdate();
            } catch (Exception e) {
                log.error("❌ Ошибка при закрытия торговли для пары: {}", pairData.getPairName(), e);
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

            BigDecimal usdtProfit = safeScale(pairDataService.getUnrealizedProfitUSDTTotal(), 2);
            BigDecimal percentProfit = safeScale(pairDataService.getUnrealizedProfitPercentTotal(), 2);

            String label = String.format("💰 Нереализованный профит: %s$/%s%%", usdtProfit, percentProfit);
            unrealizedProfitLayout.add(new H2(label));
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении нереализованного профита", e);
        }
    }

    public void updateErrorPairs() {
        try {
            List<PairData> pairs = pairDataService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.ERROR);
            errorPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating error pairs", e);
        }
    }

    public void updateObservedPairs() {
        try {
            List<PairData> pairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.OBSERVED);
            observedPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating observed pairs", e);
        }
    }

    public void updateAllData() {
        updateSelectedPairs();
        updateTradingPairs();
        updateClosedPairs();
        updateUnrealizedProfit();
        updateErrorPairs();
        updateObservedPairs();
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
    private HorizontalLayout createSelectedPairsChartActionButtons(PairData pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);

        // Кнопка Chart
        Button chartButton = createChartButton(pair);
        buttonsLayout.add(chartButton);
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Selected Pairs Grid
     */
    private HorizontalLayout createSelectedPairsStartTradingActionButtons(PairData pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);

        // Кнопка Торговать
        Button tradeButton = createStartTradingButton(pair);
        buttonsLayout.add(tradeButton);
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Trading Pairs Grid
     */
    private HorizontalLayout createTradingPairsChartActionButtons(PairData pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);
        buttonsLayout.add(createChartButton(pair));
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Trading Pairs Grid
     */
    private HorizontalLayout createTradingPairsCloseActionButtons(PairData pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);

        // Кнопка Закрыть
        Button closeButton = createStopTradingButton(pair);

        buttonsLayout.add(closeButton);
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Closed Pairs Grid (только Chart)
     */
    private Button createClosedPairsActionButtons(PairData pair) {
        return createChartButton(pair);
    }

    private Button createErrorPairsActionButtons(PairData pair) {
        return createChartButton(pair);
    }

    private Checkbox createCloseAtBreakevenCheckbox(PairData pairData) {
        Checkbox checkbox = new Checkbox(pairData.isCloseAtBreakeven());
        checkbox.addValueChangeListener(event -> {
            pairData.setCloseAtBreakeven(event.getValue());
            pairDataService.save(pairData);
            Notification.show(String.format("Для пары %s закрытие в БУ %s",
                    pairData.getPairName(),
                    event.getValue() ? "включено" : "отключено"));
        });
        return checkbox;
    }

    /**
     * Создает кнопку Chart для отображения Z-Score графика
     */
    private Button createChartButton(PairData pair) {
        Button chartButton = new Button(VaadinIcon.LINE_CHART.create());
        chartButton.getElement().setAttribute("title", "Показать график");
        chartButton.getStyle().set("color", "#2196F3");

        chartButton.addClickListener(event -> {
            try {
                log.debug("📊 Открываем чарт для пары: {}", pair.getPairName());
                zScoreChartDialog.showChart(pair);
            } catch (Exception e) {
                log.error("❌ Ошибка при показе чарта для пары: {}", pair.getPairName(), e);
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
        errorPairsGrid.setVisible(false);
        observedPairsGrid.setVisible(false);
        unrealizedProfitLayout.setVisible(false);
    }

    public void showOnlyTradingPairs() {
        selectedPairsGrid.setVisible(false);
        tradingPairsGrid.setVisible(true);
        closedPairsGrid.setVisible(false);
        errorPairsGrid.setVisible(false);
        observedPairsGrid.setVisible(false);
        unrealizedProfitLayout.setVisible(true);
    }

    public void showOnlyClosedPairs() {
        selectedPairsGrid.setVisible(false);
        tradingPairsGrid.setVisible(false);
        closedPairsGrid.setVisible(true);
        errorPairsGrid.setVisible(false);
        observedPairsGrid.setVisible(false);
        unrealizedProfitLayout.setVisible(false);
    }

    public void showOnlyErrorPairs() {
        selectedPairsGrid.setVisible(false);
        tradingPairsGrid.setVisible(false);
        closedPairsGrid.setVisible(false);
        errorPairsGrid.setVisible(true);
        observedPairsGrid.setVisible(false);
        unrealizedProfitLayout.setVisible(false);
    }

    public void showOnlyObservedPairs() {
        selectedPairsGrid.setVisible(false);
        tradingPairsGrid.setVisible(false);
        closedPairsGrid.setVisible(false);
        errorPairsGrid.setVisible(false);
        observedPairsGrid.setVisible(true);
        unrealizedProfitLayout.setVisible(false);
    }

    public void showAllPairs() {
        selectedPairsGrid.setVisible(true);
        tradingPairsGrid.setVisible(true);
        closedPairsGrid.setVisible(true);
        errorPairsGrid.setVisible(true);
        observedPairsGrid.setVisible(true);
        unrealizedProfitLayout.setVisible(true);
    }

    public void closeAllTrades() {
        try {
            List<PairData> tradingPairs = pairDataService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            for (PairData pairData : tradingPairs) {
                updateTradeProcessor.updateTrade(UpdateTradeRequest.builder()
                        .pairData(pairData)
                        .closeManually(true)
                        .build());
            }
            Notification.show(String.format("Закрыто %d пар", tradingPairs.size()));
            notifyUIUpdate();
        } catch (Exception e) {
            log.error("❌ Ошибка при закрытии всех пар", e);
            Notification.show("Ошибка при закрытии всех пар: " + e.getMessage());
        }
    }
}