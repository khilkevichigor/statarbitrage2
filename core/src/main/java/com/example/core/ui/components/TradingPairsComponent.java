package com.example.core.ui.components;

import com.example.core.processors.StartNewTradeProcessor;
import com.example.core.processors.UpdateTradeProcessor;
import com.example.core.services.AveragingService;
import com.example.core.services.PairService;
import com.example.core.services.SettingsService;
import com.example.shared.dto.StartNewTradeRequest;
import com.example.shared.dto.UpdateTradeRequest;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
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

    private final PairService pairService;
    private final StartNewTradeProcessor startNewTradeProcessor;
    private final UpdateTradeProcessor updateTradeProcessor;
    private final ZScoreChartDialog zScoreChartDialog;
    private final AveragingService averagingService;
    private final SettingsService settingsService;

    private final Grid<Pair> selectedPairsGrid;
    private final Grid<Pair> tradingPairsGrid;
    private final Grid<Pair> closedPairsGrid;
    private final Grid<Pair> errorPairsGrid;
    private final Grid<Pair> observedPairsGrid;
    private final VerticalLayout unrealizedProfitLayout;

    private Consumer<Void> uiUpdateCallback;

    public TradingPairsComponent(
            PairService pairService,
            StartNewTradeProcessor startNewTradeProcessor,
            UpdateTradeProcessor updateTradeProcessor,
            ZScoreChartDialog zScoreChartDialog,
            AveragingService averagingService,
            SettingsService settingsService
    ) {
        this.pairService = pairService;
        this.startNewTradeProcessor = startNewTradeProcessor;
        this.updateTradeProcessor = updateTradeProcessor;
        this.zScoreChartDialog = zScoreChartDialog;
        this.averagingService = averagingService;
        this.settingsService = settingsService;

        this.selectedPairsGrid = new Grid<>(Pair.class, false);
        this.tradingPairsGrid = new Grid<>(Pair.class, false);
        this.closedPairsGrid = new Grid<>(Pair.class, false);
        this.errorPairsGrid = new Grid<>(Pair.class, false);
        this.observedPairsGrid = new Grid<>(Pair.class, false);
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
        setupPairsGrid();
        setupClosedPairsGrid();
        setupErrorPairsGrid();
        setupObservedPairsGrid();
        setupCommonGridProperties();
    }

    private void setupSelectedPairsGrid() {
        selectedPairsGrid.addColumn(Pair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(Pair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent() != null ? p.getZScoreCurrent().doubleValue() : 0.0)).setHeader("Z-скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent() != null ? p.getPValueCurrent().doubleValue() : 0.0)).setHeader("PValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent() != null ? p.getAdfPvalueCurrent().doubleValue() : 0.0)).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent() != null ? p.getCorrelationCurrent().doubleValue() : 0.0)).setHeader("Корр.").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(Pair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> String.valueOf(p.getSettingsCandleLimit() != null ? p.getSettingsCandleLimit().intValue() : 0)).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        selectedPairsGrid.addColumn(p -> {
            if (p.getMinVolMln() != null) {
                // Если число целое, показываем без десятичной части
                if (p.getMinVolMln().stripTrailingZeros().scale() <= 0) {
                    return p.getMinVolMln().stripTrailingZeros().toPlainString() + " млн $";
                } else {
                    return p.getMinVolMln().stripTrailingZeros().toPlainString() + " млн $";
                }
            }
            return "-";
        }).setHeader("Min Vol").setSortable(true).setWidth("100px").setFlexGrow(0);

        selectedPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getTimestamp())).setHeader("Дата/время").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        selectedPairsGrid.addColumn(new ComponentRenderer<>(this::createSelectedPairsChartActionButtons)).setHeader("Чарт");
        selectedPairsGrid.addColumn(new ComponentRenderer<>(this::createSelectedPairsStartTradingActionButtons)).setHeader("Торговать");
    }

    private void setupPairsGrid() {
        tradingPairsGrid.addColumn(Pair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(Pair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createPairsChartActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        tradingPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> {
            if (p.getFormattedProfitCommon() != null && !p.getFormattedProfitCommon().isEmpty()) {
                return p.getFormattedProfitCommon();
            }
            return p.getProfitPercentChanges() != null ? safeScale(p.getProfitPercentChanges(), 2) + "%" : "0.00%"; //todo сетим только это! $ не сетим в getFormattedProfitCommon вобще
        }).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> p.getAveragingCount() != null ? String.valueOf(p.getAveragingCount()) : "0").setHeader("Усреднений").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> {
            if (p.getUpdatedTime() != null && p.getEntryTime() != null) {
                long duration = p.getUpdatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() -
                        p.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                return TimeFormatterUtil.formatDurationFromMillis(duration);
            }
            return "N/A";
        }).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry() != null ? p.getZScoreEntry().doubleValue() : 0.0)).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent() != null ? p.getZScoreCurrent().doubleValue() : 0.0)).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> {
            if (p.getFormattedTimeToMinProfit() != null && !p.getFormattedTimeToMinProfit().isEmpty()) {
                return p.getFormattedTimeToMinProfit();
            }
            return p.getMinutesToMinProfitPercent() != null ? p.getMinutesToMinProfitPercent() + " мин" : "-";
        }).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> {
            if (p.getFormattedTimeToMaxProfit() != null && !p.getFormattedTimeToMaxProfit().isEmpty()) {
                return p.getFormattedTimeToMaxProfit();
            }
            return p.getMinutesToMaxProfitPercent() != null ? p.getMinutesToMaxProfitPercent() + " мин" : "-";
        }).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> {
            if (p.getFormattedProfitLong() != null && !p.getFormattedProfitLong().isEmpty()) {
                return p.getFormattedProfitLong();
            }
            return p.getLongPercentChanges() != null ? safeScale(p.getLongPercentChanges(), 2) + "%" : "0.00%";
        }).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> {
            if (p.getFormattedProfitShort() != null && !p.getFormattedProfitShort().isEmpty()) {
                return p.getFormattedProfitShort();
            }
            return p.getShortPercentChanges() != null ? safeScale(p.getShortPercentChanges(), 2) + "%" : "0.00%";
        }).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry() != null ? p.getPValueEntry().doubleValue() : 0.0)).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent() != null ? p.getPValueCurrent().doubleValue() : 0.0)).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry() != null ? p.getAdfPvalueEntry().doubleValue() : 0.0)).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent() != null ? p.getAdfPvalueCurrent().doubleValue() : 0.0)).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry() != null ? p.getCorrelationEntry().doubleValue() : 0.0)).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent() != null ? p.getCorrelationCurrent().doubleValue() : 0.0)).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(Pair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        tradingPairsGrid.addColumn(p -> String.valueOf(p.getSettingsCandleLimit() != null ? p.getSettingsCandleLimit().intValue() : 0)).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(p -> {
            if (p.getEntryTime() != null) {
                return TimeFormatterUtil.formatFromMillis(p.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            return "N/A";
        }).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createCloseAtBreakevenCheckbox)).setHeader("Закрыть в БУ").setSortable(true).setAutoWidth(true);
        tradingPairsGrid.addColumn(new ComponentRenderer<>(this::createPairsCloseActionButtons)).setHeader("Действие").setSortable(true).setAutoWidth(true);
    }

    private void setupClosedPairsGrid() {
        closedPairsGrid.addColumn(Pair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(Pair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(Pair::getExitReason).setHeader("Причина выхода").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(new ComponentRenderer<>(this::createClosedPairsActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        closedPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> {
            if (p.getFormattedProfitCommon() != null && !p.getFormattedProfitCommon().isEmpty()) {
                return p.getFormattedProfitCommon();
            }
            return p.getProfitPercentChanges() != null ? safeScale(p.getProfitPercentChanges(), 2) + "%" : "0.00%";
        }).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> p.getAveragingCount() != null ? String.valueOf(p.getAveragingCount()) : "0").setHeader("Усреднений").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> safeScale(p.getPortfolioAfterTradeUSDT(), 2) + "$").setHeader("Баланс ПОСЛЕ").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> {
            if (p.getUpdatedTime() != null && p.getEntryTime() != null) {
                long duration = p.getUpdatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() -
                        p.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                return TimeFormatterUtil.formatDurationFromMillis(duration);
            }
            return "N/A";
        }).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry() != null ? p.getZScoreEntry().doubleValue() : 0.0)).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent() != null ? p.getZScoreCurrent().doubleValue() : 0.0)).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> {
            if (p.getFormattedTimeToMinProfit() != null && !p.getFormattedTimeToMinProfit().isEmpty()) {
                return p.getFormattedTimeToMinProfit();
            }
            return p.getMinutesToMinProfitPercent() != null ? p.getMinutesToMinProfitPercent() + " мин" : "-";
        }).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> {
            if (p.getFormattedTimeToMaxProfit() != null && !p.getFormattedTimeToMaxProfit().isEmpty()) {
                return p.getFormattedTimeToMaxProfit();
            }
            return p.getMinutesToMaxProfitPercent() != null ? p.getMinutesToMaxProfitPercent() + " мин" : "-";
        }).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> {
            if (p.getFormattedProfitLong() != null && !p.getFormattedProfitLong().isEmpty()) {
                return p.getFormattedProfitLong();
            }
            return p.getLongPercentChanges() != null ? safeScale(p.getLongPercentChanges(), 2) + "%" : "0.00%";
        }).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> {
            if (p.getFormattedProfitShort() != null && !p.getFormattedProfitShort().isEmpty()) {
                return p.getFormattedProfitShort();
            }
            return p.getShortPercentChanges() != null ? safeScale(p.getShortPercentChanges(), 2) + "%" : "0.00%";
        }).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry() != null ? p.getPValueEntry().doubleValue() : 0.0)).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent() != null ? p.getPValueCurrent().doubleValue() : 0.0)).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry() != null ? p.getAdfPvalueEntry().doubleValue() : 0.0)).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent() != null ? p.getAdfPvalueCurrent().doubleValue() : 0.0)).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry() != null ? p.getCorrelationEntry().doubleValue() : 0.0)).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent() != null ? p.getCorrelationCurrent().doubleValue() : 0.0)).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(Pair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> String.valueOf(p.getSettingsCandleLimit() != null ? p.getSettingsCandleLimit().intValue() : 0)).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        closedPairsGrid.addColumn(p -> {
            if (p.getEntryTime() != null) {
                return TimeFormatterUtil.formatFromMillis(p.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            return "N/A";
        }).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        closedPairsGrid.addColumn(p -> {
            if (p.getUpdatedTime() != null) {
                return TimeFormatterUtil.formatFromMillis(p.getUpdatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            return "N/A";
        }).setHeader("Конец трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
    }

    private void setupErrorPairsGrid() {
        errorPairsGrid.addColumn(Pair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(Pair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(Pair::getErrorDescription).setHeader("Ошибка").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(new ComponentRenderer<>(this::createErrorPairsActionButtons)).setHeader("Чарт").setSortable(true).setAutoWidth(true);

        errorPairsGrid.addColumn(p -> safeScale(p.getPortfolioBeforeTradeUSDT(), 2) + "$").setHeader("Баланс ДО").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(Pair::getFormattedProfitCommon).setHeader("Профит Общий").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> String.valueOf(p.getAveragingCount())).setHeader("Усреднений").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> safeScale(p.getPortfolioAfterTradeUSDT(), 2) + "$").setHeader("Баланс ПОСЛЕ").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreEntry() != null ? p.getZScoreEntry().doubleValue() : 0.0)).setHeader("Z-скор (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent() != null ? p.getZScoreCurrent().doubleValue() : 0.0)).setHeader("Z-скор (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> safeScale(p.getMaxZ(), 2)).setHeader("Z-скор (Max)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> safeScale(p.getMinZ(), 2)).setHeader("Z-скор (Min)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(Pair::getFormattedTimeToMinProfit).setHeader("Минут до min профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(Pair::getFormattedTimeToMaxProfit).setHeader("Минут до max профит").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(Pair::getFormattedProfitLong).setHeader("Профит Long").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(Pair::getFormattedProfitShort).setHeader("Профит Short").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueEntry() != null ? p.getPValueEntry().doubleValue() : 0.0)).setHeader("Pvalue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent() != null ? p.getPValueCurrent().doubleValue() : 0.0)).setHeader("Pvalue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueEntry() != null ? p.getAdfPvalueEntry().doubleValue() : 0.0)).setHeader("AdfValue (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent() != null ? p.getAdfPvalueCurrent().doubleValue() : 0.0)).setHeader("AdfValue (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationEntry() != null ? p.getCorrelationEntry().doubleValue() : 0.0)).setHeader("Corr (entry)").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent() != null ? p.getCorrelationCurrent().doubleValue() : 0.0)).setHeader("Corr (curr)").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        errorPairsGrid.addColumn(Pair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> String.valueOf(p.getSettingsCandleLimit() != null ? p.getSettingsCandleLimit().intValue() : 0)).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);

//        errorPairsGrid.addColumn(p -> TimeFormatterUtil.formatFromMillis(p.getEntryTime())).setHeader("Начало трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        errorPairsGrid.addColumn(p -> {
            if (p.getUpdatedTime() != null) {
                return TimeFormatterUtil.formatFromMillis(p.getUpdatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            return "N/A";
        }).setHeader("Дата").setSortable(true).setAutoWidth(true).setFlexGrow(0);
//        errorPairsGrid.addColumn(p -> TimeFormatterUtil.formatDurationFromMillis(p.getUpdatedTime() - p.getEntryTime())).setHeader("Продолжительность трейда").setSortable(true).setAutoWidth(true).setFlexGrow(0);

//        errorPairsGrid.addColumn(PairData::getExitReason).setHeader("Причина выхода").setSortable(true).setAutoWidth(true).setFlexGrow(0);
    }

    private void setupObservedPairsGrid() {
        observedPairsGrid.addColumn(Pair::getLongTicker).setHeader("Лонг").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(Pair::getShortTicker).setHeader("Шорт").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        observedPairsGrid.addColumn(new ComponentRenderer<>(this::createChartButton)).setHeader("Чарт");

        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getZScoreCurrent() != null ? p.getZScoreCurrent().doubleValue() : 0.0)).setHeader("Z-скор").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getPValueCurrent() != null ? p.getPValueCurrent().doubleValue() : 0.0)).setHeader("PValue").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getAdfPvalueCurrent() != null ? p.getAdfPvalueCurrent().doubleValue() : 0.0)).setHeader("AdfValue").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> NumberFormatter.formatBigDecimal(p.getCorrelationCurrent() != null ? p.getCorrelationCurrent().doubleValue() : 0.0)).setHeader("Corr").setSortable(true).setAutoWidth(true).setFlexGrow(0);

        observedPairsGrid.addColumn(Pair::getSettingsTimeframe).setHeader("ТФ").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> String.valueOf(p.getSettingsCandleLimit() != null ? p.getSettingsCandleLimit().intValue() : 0)).setHeader("Свечей").setSortable(true).setAutoWidth(true).setFlexGrow(0);
        observedPairsGrid.addColumn(p -> {
            if (p.getMinVolMln() != null) {
                // Если число целое, показываем без десятичной части
                if (p.getMinVolMln().stripTrailingZeros().scale() <= 0) {
                    return p.getMinVolMln().stripTrailingZeros().toPlainString() + " млн $";
                } else {
                    return p.getMinVolMln().stripTrailingZeros().toPlainString() + " млн $";
                }
            }
            return "-";
        }).setHeader("Min Vol").setSortable(true).setWidth("100px").setFlexGrow(0);

        observedPairsGrid.addColumn(p -> {
            if (p.getUpdatedTime() != null) {
                return TimeFormatterUtil.formatFromMillis(p.getUpdatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            return "N/A";
        }).setHeader("Дата/время").setSortable(true).setAutoWidth(true).setFlexGrow(0);
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

    private Button createStartTradingButton(Pair tradingPair) {
        Button actionButton = new Button("Торговать", event -> {
            ConfirmationDialog dialog = new ConfirmationDialog("Подтверждение", "Вы уверены, что хотите начать торговлю для пары " + tradingPair.getPairName() + "?", e -> {
                try {
                    // Ручной запуск - НЕ проверяем автотрейдинг
                    Pair newPair = startNewTradeProcessor.startNewTrade(StartNewTradeRequest.builder()
                            .tradingPair(tradingPair)
                            .checkAutoTrading(false)
                            .build());
                    if (newPair != null) {
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

    private Button createStopTradingButton(Pair tradingPair) {
        Button actionButton = new Button("Закрыть", event -> {
            ConfirmationDialog dialog = new ConfirmationDialog("Подтверждение", "Вы уверены, что хотите закрыть торговлю для пары " + tradingPair.getPairName() + "?", e -> {
                try {
                    Pair updatedPair = updateTradeProcessor.updateTrade(UpdateTradeRequest.builder()
                            .tradingPair(tradingPair)
                            .closeManually(true)
                            .build());
                    Notification.show(String.format(
                            "Статус пары %s изменен на %s",
                            updatedPair.getPairName(), updatedPair.getStatus()
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

    private Button createAveragingButton(Pair tradingPair) {
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
            List<Pair> pairs = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.SELECTED);
            selectedPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating selected pairs", e);
        }
    }

    public void updatePairs() {
        try {
            List<Pair> pairs = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            tradingPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating trading pairs", e);
        }
    }

    public void updateClosedPairs() {
        try {
            List<Pair> pairs = pairService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.CLOSED);
            closedPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating closed pairs", e);
        }
    }

    public void updateUnrealizedProfit() {
        try {
            unrealizedProfitLayout.removeAll();

            BigDecimal usdtProfit = safeScale(pairService.getUnrealizedProfitUSDTTotal(), 2);
            BigDecimal percentProfit = safeScale(pairService.getUnrealizedProfitPercentTotal(), 2);

            String label = String.format("💰 Нереализованный профит: %s$/%s%%", usdtProfit, percentProfit);
            unrealizedProfitLayout.add(new H2(label));
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении нереализованного профита", e);
        }
    }

    public void updateErrorPairs() {
        try {
            List<Pair> pairs = pairService.findAllByStatusOrderByUpdatedTimeDesc(TradeStatus.ERROR);
            errorPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating error pairs", e);
        }
    }

    public void updateObservedPairs() {
        try {
            List<Pair> pairs = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.OBSERVED);
            observedPairsGrid.setItems(pairs);
        } catch (Exception e) {
            log.error("Error updating observed pairs", e);
        }
    }

    public void updateAllData() {
        updateSelectedPairs();
        updatePairs();
        updateClosedPairs();
        updateUnrealizedProfit();
        updateErrorPairs();
        updateObservedPairs();
    }

    public void setSelectedPairs(List<Pair> pairs) {
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
    private HorizontalLayout createSelectedPairsChartActionButtons(Pair pair) {
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
    private HorizontalLayout createSelectedPairsStartTradingActionButtons(Pair pair) {
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
    private HorizontalLayout createPairsChartActionButtons(Pair pair) {
        HorizontalLayout buttonsLayout = new HorizontalLayout();
        buttonsLayout.setSpacing(true);
        buttonsLayout.setPadding(false);
        buttonsLayout.add(createChartButton(pair));
        return buttonsLayout;
    }

    /**
     * Создает кнопки действий для Trading Pairs Grid
     */
    private HorizontalLayout createPairsCloseActionButtons(Pair pair) {
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
    private Button createClosedPairsActionButtons(Pair pair) {
        return createChartButton(pair);
    }

    private Button createErrorPairsActionButtons(Pair pair) {
        return createChartButton(pair);
    }

    private Checkbox createCloseAtBreakevenCheckbox(Pair tradingPair) {
        Checkbox checkbox = new Checkbox(tradingPair.isCloseAtBreakeven());
        checkbox.addValueChangeListener(event -> {
            tradingPair.setCloseAtBreakeven(event.getValue());
            pairService.save(tradingPair);
            Notification.show(String.format("Для пары %s закрытие в БУ %s",
                    tradingPair.getPairName(),
                    event.getValue() ? "включено" : "отключено"));
        });
        return checkbox;
    }

    /**
     * Создает кнопку Chart для отображения Z-Score графика
     */
    private Button createChartButton(Pair pair) {
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

    public void showOnlyPairs() {
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
            List<Pair> tradingPairs = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
            for (Pair tradingPair : tradingPairs) {
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
            List<Pair> tradingPairs = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
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
