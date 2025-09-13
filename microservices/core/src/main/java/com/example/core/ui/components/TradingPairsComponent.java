package com.example.core.ui.components;

import com.example.core.processors.StartNewTradeProcessor;
import com.example.core.processors.UpdateTradeProcessor;
import com.example.core.services.AveragingService;
import com.example.core.services.SettingsService;
import com.example.core.services.TradingPairService;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.dto.UpdateTradeRequest;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradingPair;
import com.example.shared.utils.NumberFormatter;
import com.example.shared.utils.TimeFormatterUtil;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static com.example.shared.utils.BigDecimalUtil.safeScale;

@Slf4j
@SpringComponent
@UIScope
public class TradingPairsComponent extends VerticalLayout {

    private final TradingPairService tradingPairService;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final ZScoreChartDialog zScoreChartDialog;
    private final AveragingService averagingService;
    private final SettingsService settingsService;

    private final Grid<TradingPair> selectedPairsGrid;
    private final Grid<TradingPair> tradingPairsGrid;
    private final Grid<TradingPair> closedPairsGrid;
    private final Grid<TradingPair> errorPairsGrid;
    private final Grid<TradingPair> observedPairsGrid;
    private final VerticalLayout unrealizedProfitLayout;

    private Consumer<Void> uiUpdateCallback;

    public TradingPairsComponent(
            TradingPairService tradingPairService,
            StartNewTradeProcessor startNewTradeProcessor,
            UpdateTradeProcessor updateTradeProcessor,
            ZScoreChartDialog zScoreChartDialog,
            AveragingService averagingService,
            SettingsService settingsService
    ) {
        this.tradingPairService = tradingPairService;
        this.startNewTradeProcessor = startNewTradeProcessor;
        this.updateTradeProcessor = updateTradeProcessor;
        this.zScoreChartDialog = zScoreChartDialog;
        this.averagingService = averagingService;
        this.settingsService = settingsService;

        this.selectedPairsGrid = new Grid<>(TradingPair.class, false);
        this.tradingPairsGrid = new Grid<>(TradingPair.class, false);
        this.closedPairsGrid = new Grid<>(TradingPair.class, false);
        this.errorPairsGrid = new Grid<>(TradingPair.class, false);
        this.observedPairsGrid = new Grid<>(TradingPair.class, false);
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
        selectedPairsGrid.addColumn(TradingPair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(TradingPair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("PValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Корр.").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(TradingPair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> String.valueOf((int) p.getSettingsCandleLimit())).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getTimestamp())).setHeader("Дата/время").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(new ComponentRenderer<>(this::createSelectedPairsChartActionButtons)).setHeader("Чарт");
        selectedPairsGrid.addColumn(new ComponentRenderer<>(this::createSelectedPairsStartTradingActionButtons)).setHeader("Торговать");
    }

    private void setupTradingPairsGrid() {
        tradingPairsGrid.addColumn(TradingPair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(TradingPair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createTradingPairsChartActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        tradingPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(TradingPair::getFormattedProfitCommon).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(TradingPair::getFormattedAveragingCount).setHeader("Усреднений").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> TimeFormatterUtil.formatDurationFromMillis(p.getUpdatedTime() - p.getEntryTime())).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry())).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(TradingPair::getFormattedTimeToMinProfit).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(TradingPair::getFormattedTimeToMaxProfit).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(TradingPair::getFormattedProfitLong).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(TradingPair::getFormattedProfitShort).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry())).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry())).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry())).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(TradingPair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> String.valueOf((int) p.getSettingsCandleLimit())).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getEntryTime())).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createCloseAtBreakevenCheckbox)).setHeader("Закрыть в БУ").setSortable(true).setAutoWidth(true);
        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createTradingPairsCloseActionButtons)).setHeader("Действие").setSortable(true).setAutoWidth(true);
    }

    private void setupClosedPairsGrid() {
        closedPairsGrid.addColumn(TradingPair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(TradingPair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(TradingPair::getExitReason).setHeader("Причина выхода").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(new ComponentRenderer<>(this::createClosedPairsActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        closedPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(TradingPair::getFormattedProfitCommon).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(TradingPair::getFormattedAveragingCount).setHeader("Усреднений").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> safeScale(p.getPortfolioAfterTradeUSDT(), 2) + "$").setHeader("Баланс ПОСЛЕ").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> TimeFormatterUtil.formatDurationFromMillis(p.getUpdatedTime() - p.getEntryTime())).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry())).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(TradingPair::getFormattedTimeToMinProfit).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(TradingPair::getFormattedTimeToMaxProfit).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(TradingPair::getFormattedProfitLong).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(TradingPair::getFormattedProfitShort).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry())).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry())).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry())).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(TradingPair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> String.valueOf((int) p.getSettingsCandleLimit())).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getEntryTime())).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getUpdatedTime())).setHeader("Конец трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
    }

    private void setupErrorPairsGrid() {
        errorPairsGrid.addColumn(TradingPair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(TradingPair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(TradingPair::getErrorDescription).setHeader("Ошибка").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(new ComponentRenderer<>(this::createErrorPairsActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        errorPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(TradingPair::getFormattedProfitCommon).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(TradingPair::getFormattedAveragingCount).setHeader("Усреднений").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> safeScale(p.getPortfolioAfterTradeUSDT(), 2) + "$").setHeader("Баланс ПОСЛЕ").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry())).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(TradingPair::getFormattedTimeToMinProfit).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(TradingPair::getFormattedTimeToMaxProfit).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(TradingPair::getFormattedProfitLong).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(TradingPair::getFormattedProfitShort).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry())).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry())).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry())).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(TradingPair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> String.valueOf((int) p.getSettingsCandleLimit())).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);

//        errorPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getEntryTime())).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getUpdatedTime())).setHeader("Дата").setSortable(true).setAutoWidth(true).setFlexGrow(0);
//        errorPairsGrid.addColumn(p -> TimeFormatterUtil.formatDurationFromMillis(p.getUpdatedTime() - p.getEntryTime())).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

//        errorPairsGrid.addColumn(PairData::getExitReason).setHeader("Причина выхода").setSortable(true).setAutoWidth(true).setFlexGrow(0);
    }

    private void setupObservedPairsGrid() {
        observedPairsGrid.addColumn(TradingPair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(TradingPair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        observedPairsGrid.addColumn(new ComponentRenderer<>(this::createChartButton)).setHeader("Чарт");

        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent())).setHeader("Z-скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent())).setHeader("PValue").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent())).setHeader("AdfValue").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent())).setHeader("Corr").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        observedPairsGrid.addColumn(TradingPair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> String.valueOf((int) p.getSettingsCandleLimit())).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);

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

    private Button createStartTradingButton(TradingPair tradingPair) {
        Button actionButton = new Button("Торговать", event -> {
            ConfirmationDialog dialog = new ConfirmationDialog("Подтверждение", "Вы уверены, что хотите начать торговлю для пары " + tradingPair.getPairName() + "?", e -> {
                try {
                    // Ручной запуск - НЕ проверяем автотрейдинг
                    TradingPair newTradingPair = startNewTradeProcessor.startNewTrade(StartNewTradeRequest.builder()
                            .tradingPair(tradingPair)
                            .checkAutoTrading(false)
                            .build());
                    if (newTradingPair != null) {
                        Notification.show(String.format(
                                "Статус пары %s изменен на %s",
                                tradingPair.getPairName(), TradeStatus.TRADING
                        ));
                    } else {
                        Notification.show(String.format(
                                "Пропускаем новый трейд для пары %s",
                                tradingPair.getPairName()
                        ));
                    }

                    notifyUIUpdate();
                } catch (Exception ex) {
                    log.error("❌ Ошибка начала торговли для пары: {}", tradingPair.getPairName(), ex);
                    Notification.show("Ошибка при открытии торговли: " + ex.getMessage());
                }
            });
            dialog.open();
        });

        actionButton.getStyle().set("color", "green");
        return actionButton;
    }

    private Button createStopTradingButton(TradingPair tradingPair) {
        Button actionButton = new Button("Закрыть", event -> {
            ConfirmationDialog dialog = new ConfirmationDialog("Подтверждение", "Вы уверены, что хотите закрыть торговлю для пары " + tradingPair.getPairName() + "?", e -> {
                try {
                    TradingPair updatedTradingPair = updateTradeProcessor.updateTrade(UpdateTradeRequest.builder()
                            .tradingPair(tradingPair)
                            .closeManually(true)
                            .build());
                    Notification.show(String.format(
                            "Статус пары %s изменен на %s",
                            updatedTradingPair.getPairName(), updatedTradingPair.getStatus()
                    ));
                    notifyUIUpdate();
                } catch (Exception ex) {
                    log.error("❌ Ошибка при закрытия торговли для пары: {}", tradingPair.getPairName(), ex);
                    Notification.show("Ошибка при закрытии торговли: " + ex.getMessage());
                }
            });
            dialog.open();
        });

        actionButton.getStyle().set("color", "red");
        return actionButton;
    }

    private Button createAveragingButton(TradingPair tradingPair) {
        Button actionButton = new Button("Усреднить", event -> {
            ConfirmationDialog dialog = new ConfirmationDialog("Подтверждение", "Вы уверены, что хотите усреднить пару " + tradingPair.getPairName() + "?", e -> {
                try {
                    var settings = settingsService.getSettings();
                    var result = averagingService.performManualAveraging(tradingPair, settings);

                    if (result.isSuccess()) {
                        Notification.show(result.getMessage());
                        log.debug("✅ Усреднение выполнено для пары: {}", tradingPair.getPairName());
                    } else {
                        Notification.show("Ошибка: " + result.getMessage());
                        log.error("❌ Ошибка усреднения для пары: {}", tradingPair.getPairName());
                    }

                    notifyUIUpdate();
                } catch (Exception ex) {
                    log.error("❌ Ошибка при усреднении для пары: {}", tradingPair.getPairName(), ex);
                    Notification.show("Ошибка при усреднении: " + ex.getMessage());
                }
            });
            dialog.open();
        });

        actionButton.getStyle().set("color", "orange");
        return actionButton;
    }

    public void updateSelectedPairs() {
        try {
            List<TradingPair> pairs = tradingPairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.SELECTED);
            selectedPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating selected pairs", e);
        }
    }

    public void updateTradingPairs() {
        try {
            List<TradingPair> pairs = tradingPairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            tradingPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating trading pairs", e);
        }
    }

    public void updateClosedPairs() {
        try {
            List<TradingPair> pairs = tradingPairService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.CLOSED);
            closedPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating closed pairs", e);
        }
    }

    public void updateUnrealizedProfit() {
        try {
            unrealizedProfitLayout.removeAll();

            BigDecimal usdtProfit = safeScale(tradingPairService.getUnrealizedProfitUSDTTotal(), 2);
            BigDecimal percentProfit = safeScale(tradingPairService.getUnrealizedProfitPercentTotal(), 2);

            String label = String.format("💰 Нереализованный профит: %s$/%s%%", usdtProfit, percentProfit);
            unrealizedProfitLayout.add(new H2(label));
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении нереализованного профита", e);
        }
    }

    public void updateErrorPairs() {
        try {
            List<TradingPair> pairs = tradingPairService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.ERROR);
            errorPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating error pairs", e);
        }
    }

    public void updateObservedPairs() {
        try {
            List<TradingPair> pairs = tradingPairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.OBSERVED);
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

    public void setSelectedPairs(List<TradingPair> pairs) {
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
    private HorizontalLayout createSelectedPairsChartActionButtons(TradingPair pair) {
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
    private HorizontalLayout createSelectedPairsStartTradingActionButtons(TradingPair pair) {
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
    private HorizontalLayout createTradingPairsChartActionButtons(TradingPair pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);
        buttonsLayout.add(createChartButton(pair));
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Trading Pairs Grid
     */
    private HorizontalLayout createTradingPairsCloseActionButtons(TradingPair pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);

        // Кнопка Усреднить
        Button averageButton = createAveragingButton(pair);

        // Кнопка Закрыть
        Button closeButton = createStopTradingButton(pair);

        buttonsLayout.add(averageButton, closeButton);
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Closed Pairs Grid (только Chart)
     */
    private Button createClosedPairsActionButtons(TradingPair pair) {
        return createChartButton(pair);
    }

    private Button createErrorPairsActionButtons(TradingPair pair) {
        return createChartButton(pair);
    }

    private Checkbox createCloseAtBreakevenCheckbox(TradingPair tradingPair) {
        Checkbox checkbox = new Checkbox(tradingPair.isCloseAtBreakeven());
        checkbox.addValueChangeListener(event -> {
            tradingPair.setCloseAtBreakeven(event.getValue());
            tradingPairService.save(tradingPair);
            Notification.show(String.format("Для пары %s закрытие в БУ %s",
                    tradingPair.getPairName(),
                    event.getValue() ? "включено" : "отключено"));
        });
        return checkbox;
    }

    /**
     * Создает кнопку Chart для отображения Z-Score графика
     */
    private Button createChartButton(TradingPair pair) {
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
            List<TradingPair> tradingPairs = tradingPairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            for (TradingPair tradingPair : tradingPairs) {
                updateTradeProcessor.updateTrade(UpdateTradeRequest.builder()
                        .tradingPair(tradingPair)
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

    public void closeAllTradesWithConfirmation() {
        try {
            List<TradingPair> tradingPairs = tradingPairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            if (tradingPairs.isEmpty()) {
                Notification.show("Нет активных торговых пар для закрытия");
                return;
            }
            
            ConfirmationDialog dialog = new ConfirmationDialog(
                    "Подтверждение", 
                    String.format("Вы уверены, что хотите закрыть все торговые пары (%d шт.)?", tradingPairs.size()), 
                    e -> closeAllTrades()
            );
            dialog.open();
        } catch (Exception e) {
            log.error("❌ Ошибка при подготовке закрытия всех пар", e);
            Notification.show("Ошибка при подготовке закрытия всех пар: " + e.getMessage());
        }
    }

    private static class ConfirmationDialog extends Dialog {

        public ConfirmationDialog(String header, String text, Consumer<Void> confirmListener) {
            VerticalLayout layout = new VerticalLayout();
            layout.setPadding(false);
            layout.setSpacing(true);
            layout.setAlignItems(FlexComponent.Alignment.CENTER);

            layout.add(new H4(header));
            layout.add(new Span(text));

            Button confirmButton = new Button("Да", e -> {
                confirmListener.accept(null);
                close();
            });
            Button cancelButton = new Button("Нет", e -> close());

            HorizontalLayout buttonLayout = new HorizontalLayout(confirmButton, cancelButton);
            buttonLayout.setSpacing(true);
            layout.add(buttonLayout);

            add(layout);
        }
    }
}
