package com.example.core.ui.components;

import com.example.core.services.ChartService;
import com.example.core.services.ChartSettingsService;
import com.example.core.services.PixelSpreadService;
import com.example.core.services.SettingsService;
import com.example.shared.models.ChartSettings;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;

@Slf4j
@SpringComponent
@UIScope
public class ZScoreChartDialog extends Dialog {

    private static final String CHART_TYPE = "ZSCORE_CHART_DIALOG";

    private final SettingsService settingsService;
    private final ChartService chartService;
    private final PixelSpreadService pixelSpreadService;
    private final ChartSettingsService chartSettingsService;

    private VerticalLayout content;
    private Image mainChartImage; // Единая область для чартов
    private Image intersectionsChartImage; // Чарт пересечений
    private Div dataInfoPanel; // Панель с информацией о данных
    private H3 pairTitle;
    private Div detailsPanel;
    // Чекбоксы для выбора типов чартов
    private Checkbox showZScoreCheckbox;
    private Checkbox showCombinedPriceCheckbox;
    private Checkbox showPixelSpreadCheckbox;
    // Чекбоксы для дополнительных индикаторов на Z-Score
    private Checkbox showEmaCheckbox;
    private Checkbox showStochRsiCheckbox;
    private Checkbox showProfitCheckbox;
    private Checkbox showEntryPointCheckbox;
    private Pair currentPair;

    public ZScoreChartDialog(SettingsService settingsService, ChartService chartService,
                             PixelSpreadService pixelSpreadService, ChartSettingsService chartSettingsService) {
        this.settingsService = settingsService;
        this.chartService = chartService;
        this.pixelSpreadService = pixelSpreadService;
        this.chartSettingsService = chartSettingsService;
        initializeDialog();
        createComponents();
        layoutComponents();
    }

    private void initializeDialog() {
        setWidth("90vw");
        setHeight("80vh");
        setModal(true);
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);
        setResizable(true);
        setDraggable(true);
    }

    private void createComponents() {
        pairTitle = new H3();
        pairTitle.getStyle().set("margin", "0 0 1rem 0");
        pairTitle.getStyle().set("color", "var(--lumo-primary-text-color)");

        // Создаем чекбоксы для выбора типов чартов
        createChartSelectionCheckboxes();

        // Единая область для чартов
        mainChartImage = new Image();
        mainChartImage.setWidth("100%");
        mainChartImage.setHeight("600px");
        mainChartImage.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        mainChartImage.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

        // Чарт пересечений
        intersectionsChartImage = new Image();
        intersectionsChartImage.setWidth("100%");
        intersectionsChartImage.setHeight("400px");
        intersectionsChartImage.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        intersectionsChartImage.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        intersectionsChartImage.getStyle().set("margin-top", "1rem");

        // Панель с информацией о данных
        dataInfoPanel = new Div();
        dataInfoPanel.getStyle().set("padding", "1rem");
        dataInfoPanel.getStyle().set("background", "var(--lumo-primary-color-10pct)");
        dataInfoPanel.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        dataInfoPanel.getStyle().set("margin-top", "1rem");
        dataInfoPanel.getStyle().set("font-family", "monospace");
        dataInfoPanel.getStyle().set("font-size", "0.9rem");

        detailsPanel = new Div();
        detailsPanel.getStyle().set("padding", "1rem");
        detailsPanel.getStyle().set("background", "var(--lumo-contrast-5pct)");
        detailsPanel.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        detailsPanel.getStyle().set("margin-top", "1rem");

        content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(true);
        content.setAlignItems(FlexComponent.Alignment.STRETCH);
    }

    /**
     * Создает чекбоксы для выбора типов чартов
     */
    private void createChartSelectionCheckboxes() {
        Settings settings = settingsService.getSettings();

        // Загружаем сохраненные настройки чарта
        ChartSettings chartSettings = chartSettingsService.getChartSettings(CHART_TYPE);

        // Основные чекбоксы для выбора типов чартов
        showZScoreCheckbox = new Checkbox("📊 Z-Score график");
        showZScoreCheckbox.setValue(chartSettings.isShowZScore());
        showZScoreCheckbox.addValueChangeListener(e -> {
            chartSettingsService.updateChartSetting(CHART_TYPE, "showZScore", e.getValue());
            refreshMainChart();
        });

        showCombinedPriceCheckbox = new Checkbox("💰 Наложенные цены");
        showCombinedPriceCheckbox.setValue(chartSettings.isShowCombinedPrice());
        showCombinedPriceCheckbox.addValueChangeListener(e -> {
            chartSettingsService.updateChartSetting(CHART_TYPE, "showCombinedPrice", e.getValue());
            refreshMainChart();
        });

        showPixelSpreadCheckbox = new Checkbox("📏 Пиксельный спред");
        showPixelSpreadCheckbox.setValue(chartSettings.isShowPixelSpread());
        showPixelSpreadCheckbox.addValueChangeListener(e -> {
            chartSettingsService.updateChartSetting(CHART_TYPE, "showPixelSpread", e.getValue());
            refreshMainChart();
        });

        // Дополнительные индикаторы для Z-Score (только если Z-Score выбран)
        showEmaCheckbox = new Checkbox("+ EMA (" + getEmaPeriodFromTimeframe(settings.getTimeframe()) + ")");
        showEmaCheckbox.setValue(chartSettings.isShowEma());
        showEmaCheckbox.addValueChangeListener(e -> {
            chartSettingsService.updateChartSetting(CHART_TYPE, "showEma", e.getValue());
            refreshMainChart();
        });
        showEmaCheckbox.setEnabled(chartSettings.isShowZScore()); // Отключен пока Z-Score не выбран

        showStochRsiCheckbox = new Checkbox("+ StochRSI");
        showStochRsiCheckbox.setValue(chartSettings.isShowStochRsi());
        showStochRsiCheckbox.addValueChangeListener(e -> {
            chartSettingsService.updateChartSetting(CHART_TYPE, "showStochRsi", e.getValue());
            refreshMainChart();
        });
        showStochRsiCheckbox.setEnabled(chartSettings.isShowZScore()); // Отключен пока Z-Score не выбран

        showProfitCheckbox = new Checkbox("💹 Профит");
        showProfitCheckbox.setValue(chartSettings.isShowProfit());
        showProfitCheckbox.addValueChangeListener(e -> {
            chartSettingsService.updateChartSetting(CHART_TYPE, "showProfit", e.getValue());
            refreshMainChart();
        });

        showEntryPointCheckbox = new Checkbox("🎯 Показать точку входа");
        showEntryPointCheckbox.setValue(chartSettings.isShowEntryPoint());
        showEntryPointCheckbox.addValueChangeListener(e -> {
            chartSettingsService.updateChartSetting(CHART_TYPE, "showEntryPoint", e.getValue());
            refreshMainChart();
        });

        log.debug("📊 Загружены настройки чарта: ZScore={}, Price={}, Pixel={}, EMA={}, StochRSI={}, Profit={}, EntryPoint={}",
                chartSettings.isShowZScore(), chartSettings.isShowCombinedPrice(), chartSettings.isShowPixelSpread(),
                chartSettings.isShowEma(), chartSettings.isShowStochRsi(), chartSettings.isShowProfit(),
                chartSettings.isShowEntryPoint());
    }

    /**
     * Получает период EMA в зависимости от таймфрейма
     */
    private int getEmaPeriodFromTimeframe(String timeframe) {
        return switch (timeframe) {
            case "1m" -> 20;
            case "5m" -> 20;
            case "15m" -> 14;
            case "1H" -> 14;
            case "4H" -> 12;
            case "1d" -> 10;
            default -> 14;
        };
    }

    /**
     * Обновляет главный чарт с учетом выбранных типов
     */
    private void refreshMainChart() {
        if (currentPair == null) return;

        try {
            boolean showZScore = showZScoreCheckbox.getValue();
            boolean showCombinedPrice = showCombinedPriceCheckbox.getValue();
            boolean showPixelSpread = showPixelSpreadCheckbox.getValue();
            boolean showEntryPoint = showEntryPointCheckbox.getValue();

            // Управляем доступностью индикаторов Z-Score (но НЕ профит!)
            boolean zScoreEnabled = showZScore;
            showEmaCheckbox.setEnabled(zScoreEnabled);
            showStochRsiCheckbox.setEnabled(zScoreEnabled);

            // Если Z-Score отключен, отключаем его индикаторы (но НЕ профит!)
            if (!zScoreEnabled) {
                showEmaCheckbox.setValue(false);
                showStochRsiCheckbox.setValue(false);
            }

            // Проверяем, что хотя бы один чарт выбран
            if (!showZScore && !showCombinedPrice && !showPixelSpread) {
                // Если ни один не выбран, очищаем изображение
                mainChartImage.setSrc("");
                mainChartImage.setAlt("Выберите тип чарта для отображения");
                log.debug("📊 Все чекбоксы отключены - чарт очищен");
                return;
            }

            BufferedImage chartImage = null;

            // Создаем чарт в зависимости от выбранного типа
            if (showZScore && !showCombinedPrice && !showPixelSpread) {
                // Только Z-Score чарт с индикаторами
                Settings settings = settingsService.getSettings();
                boolean showEma = showEmaCheckbox.getValue();
                boolean showStochRsi = showStochRsiCheckbox.getValue();
                boolean showProfit = showProfitCheckbox.getValue();
                int emaPeriod = getEmaPeriodFromTimeframe(settings.getTimeframe());

                chartImage = chartService.createZScoreChart(currentPair, showEma, emaPeriod, showStochRsi, showProfit, false, false, showEntryPoint);
                log.debug("📊 Создан Z-Score чарт с индикаторами: EMA={}, StochRSI={}, Profit={}", showEma, showStochRsi, showProfit);

            } else if (showCombinedPrice && !showZScore && !showPixelSpread) {
                // Только Price чарт с профитом
                boolean showProfit = showProfitCheckbox.getValue();
                chartImage = chartService.createPriceChartWithProfit(currentPair, false, showProfit, showEntryPoint);
                log.debug("📊 Создан Price чарт с Profit={}", showProfit);

            } else if (showPixelSpread && !showZScore && !showCombinedPrice) {
                // Только Pixel Spread чарт с профитом
                boolean showProfit = showProfitCheckbox.getValue();
                chartImage = chartService.createPixelSpreadChartWithProfit(currentPair, showProfit, showEntryPoint);
                log.debug("📊 Создан Pixel Spread чарт с Profit={}", showProfit);

            } else {
                // Комбинированный чарт - создаем комбинированный Z-Score чарт
                Settings settings = settingsService.getSettings();
                boolean showEma = showEmaCheckbox.getValue();
                boolean showStochRsi = showStochRsiCheckbox.getValue();
                boolean showProfit = showProfitCheckbox.getValue();
                int emaPeriod = getEmaPeriodFromTimeframe(settings.getTimeframe());

                chartImage = chartService.createCombinedChart(currentPair, showZScore, showCombinedPrice, showPixelSpread, showEma, emaPeriod, showStochRsi, showProfit, showEntryPoint);
                log.debug("📊 Создан комбинированный чарт: ZScore={}, Price={}, PixelSpread={}", showZScore, showCombinedPrice, showPixelSpread);
            }

            if (chartImage != null) {
                StreamResource chartResource = createStreamResource(chartImage, "main-chart.png");
                mainChartImage.setSrc(chartResource);
                mainChartImage.setAlt("Chart for " + currentPair.getPairName());
            } else {
                mainChartImage.setSrc("");
                mainChartImage.setAlt("Failed to generate chart");
                log.warn("⚠️ Не удалось создать чарт для пары: {}", currentPair.getPairName());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении главного чарта", e);
            mainChartImage.setSrc("");
            mainChartImage.setAlt("Chart generation error");
        }
    }


    private void layoutComponents() {
        // Header with close button
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        Button closeButton = new Button(VaadinIcon.CLOSE.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.addClickListener(e -> close());

        header.add(pairTitle, closeButton);

        // Панель с чекбоксами для выбора типов чартов
        VerticalLayout chartSelectionPanel = new VerticalLayout();
        chartSelectionPanel.setSpacing(false);
        chartSelectionPanel.setPadding(true);
        chartSelectionPanel.getStyle().set("background", "var(--lumo-contrast-5pct)");
        chartSelectionPanel.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        chartSelectionPanel.getStyle().set("margin-bottom", "1rem");

        Span chartsLabel = new Span("📊 Выбор чартов:");
        chartsLabel.getStyle().set("font-weight", "bold");
        chartsLabel.getStyle().set("margin-bottom", "0.5rem");

        HorizontalLayout mainChartsRow = new HorizontalLayout();
        mainChartsRow.setAlignItems(FlexComponent.Alignment.CENTER);
        mainChartsRow.add(showZScoreCheckbox, showCombinedPriceCheckbox, showPixelSpreadCheckbox, showProfitCheckbox, showEntryPointCheckbox);

        HorizontalLayout indicatorsRow = new HorizontalLayout();
        indicatorsRow.setAlignItems(FlexComponent.Alignment.CENTER);
        indicatorsRow.getStyle().set("margin-top", "0.5rem");

        Span indicatorsLabel = new Span("📈 Индикаторы Z-Score:");
        indicatorsLabel.getStyle().set("font-size", "0.9rem");
        indicatorsLabel.getStyle().set("color", "var(--lumo-secondary-text-color)");
        indicatorsLabel.getStyle().set("margin-right", "1rem");

        indicatorsRow.add(indicatorsLabel, showEmaCheckbox, showStochRsiCheckbox);

        chartSelectionPanel.add(chartsLabel, mainChartsRow, indicatorsRow);

        // Заголовок для чарта пересечений
        H3 intersectionsTitle = new H3("📈 Чарт пересечений цен");
        intersectionsTitle.getStyle().set("margin", "1rem 0 0.5rem 0");
        intersectionsTitle.getStyle().set("color", "var(--lumo-primary-text-color)");

        content.add(header, dataInfoPanel, chartSelectionPanel, mainChartImage, intersectionsTitle, intersectionsChartImage, detailsPanel);
        add(content);
    }

    /**
     * Показать Z-Score чарт для торговой пары
     *
     * @param tradingPair данные торговой пары
     */
    public void showChart(Pair tradingPair) {
        if (tradingPair == null) {
            log.warn("⚠️ Попытка показать чарт для null PairData");
            return;
        }

        try {
            log.debug("📊 Показываем Z-Score чарт для пары: {}", tradingPair.getPairName());

            // Сохраняем текущие данные пары
            this.currentPair = tradingPair;

            // Устанавливаем заголовок
            pairTitle.setText(String.format("📊 Z-Score Chart: %s", tradingPair.getPairName()));

            // Загружаем сохраненные настройки чекбоксов из базы данных
            ChartSettings chartSettings = chartSettingsService.getChartSettings(CHART_TYPE);
            showZScoreCheckbox.setValue(chartSettings.isShowZScore());
            showCombinedPriceCheckbox.setValue(chartSettings.isShowCombinedPrice());
            showPixelSpreadCheckbox.setValue(chartSettings.isShowPixelSpread());
            showEmaCheckbox.setValue(chartSettings.isShowEma());
            showStochRsiCheckbox.setValue(chartSettings.isShowStochRsi());
            showProfitCheckbox.setValue(chartSettings.isShowProfit());
            showEntryPointCheckbox.setValue(chartSettings.isShowEntryPoint());

            // Управляем доступностью индикаторов Z-Score
            showEmaCheckbox.setEnabled(chartSettings.isShowZScore());
            showStochRsiCheckbox.setEnabled(chartSettings.isShowZScore());

            log.debug("📊 Восстановлены настройки чекбоксов: ZScore={}, Price={}, Pixel={}, EMA={}, StochRSI={}, Profit={}, EntryPoint={}",
                    chartSettings.isShowZScore(), chartSettings.isShowCombinedPrice(), chartSettings.isShowPixelSpread(),
                    chartSettings.isShowEma(), chartSettings.isShowStochRsi(), chartSettings.isShowProfit(),
                    chartSettings.isShowEntryPoint());

            // Вычисляем пиксельный спред независимо от чекбокса объединенных цен используя PixelSpreadService
            pixelSpreadService.calculatePixelSpreadIfNeeded(currentPair);

            // Генерируем и показываем чарт согласно выбранным чекбоксам
            refreshMainChart();

            // Генерируем чарт пересечений
            refreshIntersectionsChart();

            // Обновляем информацию о данных
            updateDataInfoPanel(tradingPair);

            // Заполняем детальную информацию
            updateDetailsPanel(tradingPair);

            // Открываем диалог
            open();

        } catch (Exception e) {
            log.error("❌ Ошибка при показе чарта для пары: {}", tradingPair.getPairName(), e);

            // Показываем ошибку пользователю
            pairTitle.setText("❌ Error Loading Chart");
            mainChartImage.setSrc("");
            mainChartImage.setAlt("Chart loading failed");
            detailsPanel.removeAll();
            detailsPanel.add(new Span("Failed to load chart: " + e.getMessage()));
            open();
        }
    }

    /**
     * Создает StreamResource из BufferedImage
     */
    private StreamResource createStreamResource(BufferedImage bufferedImage, String fileName) {
        return new StreamResource(fileName, () -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "png", baos);
                return new ByteArrayInputStream(baos.toByteArray());
            } catch (IOException e) {
                log.error("❌ Ошибка при создании StreamResource для чарта", e);
                throw new RuntimeException("Failed to create chart stream", e);
            }
        });
    }

    /**
     * Обновляет панель с детальной информацией о паре
     */
    private void updateDetailsPanel(Pair tradingPair) {
        detailsPanel.removeAll();

        // Создаем HTML-контент с детальной информацией
        VerticalLayout details = new VerticalLayout();
        details.setSpacing(false);
        details.setPadding(false);

        // Основные метрики
        HorizontalLayout metricsRow1 = new HorizontalLayout();
        metricsRow1.setWidthFull();

        Div zScoreInfo = createMetricDiv("Current Z-Score",
                String.format("%.3f", tradingPair.getZScoreCurrent() != null ? tradingPair.getZScoreCurrent().doubleValue() : 0.0),
                getZScoreColor(tradingPair.getZScoreCurrent() != null ? tradingPair.getZScoreCurrent().doubleValue() : 0.0));

        Div correlationInfo = createMetricDiv("Correlation",
                String.format("%.3f", tradingPair.getCorrelationCurrent() != null ? tradingPair.getCorrelationCurrent().doubleValue() : 0.0), "#2196F3");

        Div statusInfo = createMetricDiv("Status",
                tradingPair.getStatus().toString(), "#4CAF50");

        metricsRow1.add(zScoreInfo, correlationInfo, statusInfo);

        // Вторая строка метрик (если есть данные о прибыли)
        HorizontalLayout metricsRow2 = new HorizontalLayout();
        metricsRow2.setWidthFull();

        if (tradingPair.getProfitPercentChanges() != null) {
            Div profitInfo = createMetricDiv("Profit",
                    tradingPair.getProfitPercentChanges().setScale(2, RoundingMode.HALF_UP) + "%",
                    getProfitColor(tradingPair.getProfitPercentChanges().doubleValue()));
            metricsRow2.add(profitInfo);
        }

        if (tradingPair.getMaxZ() != null && tradingPair.getMinZ() != null) {
            Div maxZInfo = createMetricDiv("Max Z",
                    tradingPair.getMaxZ().setScale(3, RoundingMode.HALF_UP).toString(), "#FF9800");
            Div minZInfo = createMetricDiv("Min Z",
                    tradingPair.getMinZ().setScale(3, RoundingMode.HALF_UP).toString(), "#FF9800");
            metricsRow2.add(maxZInfo, minZInfo);
        }

        details.add(metricsRow1);
        if (metricsRow2.getComponentCount() > 0) {
            details.add(metricsRow2);
        }

        // Trading recommendations
        Div recommendationDiv = createRecommendationDiv(tradingPair.getZScoreCurrent() != null ? tradingPair.getZScoreCurrent().doubleValue() : 0.0);
        details.add(recommendationDiv);

        detailsPanel.add(details);
    }

    /**
     * Создает элемент метрики
     */
    private Div createMetricDiv(String label, String value, String color) {
        Div metricDiv = new Div();
        metricDiv.getStyle().set("text-align", "center");
        metricDiv.getStyle().set("flex", "1");
        metricDiv.getStyle().set("padding", "0.5rem");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("display", "block");
        labelSpan.getStyle().set("font-size", "0.8rem");
        labelSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("display", "block");
        valueSpan.getStyle().set("font-size", "1.2rem");
        valueSpan.getStyle().set("font-weight", "bold");
        valueSpan.getStyle().set("color", color);

        metricDiv.add(labelSpan, valueSpan);
        return metricDiv;
    }

    /**
     * Создает блок с торговыми рекомендациями
     */
    private Div createRecommendationDiv(double zScore) {
        Div recommendationDiv = new Div();
        recommendationDiv.getStyle().set("margin-top", "1rem");
        recommendationDiv.getStyle().set("padding", "1rem");
        recommendationDiv.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

        String recommendation;
        String bgColor;
        String textColor;

        if (Math.abs(zScore) > 2.0) {
            recommendation = zScore > 0 ?
                    "🔴 STRONG SIGNAL: Consider SHORT position" :
                    "🟢 STRONG SIGNAL: Consider LONG position";
            bgColor = "var(--lumo-error-color-10pct)";
            textColor = "var(--lumo-error-text-color)";
        } else if (Math.abs(zScore) > 1.0) {
            recommendation = "🟡 WEAK SIGNAL: Monitor for stronger signal";
            bgColor = "var(--lumo-warning-color-10pct)";
            textColor = "var(--lumo-warning-text-color)";
        } else {
            recommendation = "⚪ NO SIGNAL: Pair close to equilibrium";
            bgColor = "var(--lumo-contrast-5pct)";
            textColor = "var(--lumo-body-text-color)";
        }

        recommendationDiv.getStyle().set("background", bgColor);
        recommendationDiv.getStyle().set("color", textColor);
        recommendationDiv.getStyle().set("font-weight", "bold");
        recommendationDiv.setText(recommendation);

        return recommendationDiv;
    }

    /**
     * Определяет цвет для Z-Score значения
     */
    private String getZScoreColor(double zScore) {
        if (Math.abs(zScore) > 2.0) {
            return "#F44336"; // Красный для сильного сигнала
        } else if (Math.abs(zScore) > 1.0) {
            return "#FF9800"; // Оранжевый для слабого сигнала
        } else {
            return "#4CAF50"; // Зеленый для равновесия
        }
    }

    /**
     * Определяет цвет для значения прибыли
     */
    private String getProfitColor(double profit) {
        return profit >= 0 ? "#4CAF50" : "#F44336";
    }

    /**
     * Обновляет чарт пересечений
     */
    private void refreshIntersectionsChart() {
        if (currentPair == null) return;

        try {
            log.info("📊 Создание чарта пересечений для пары: {}", currentPair.getPairName());

            // Получаем данные свечей
            var longCandles = currentPair.getLongTickerCandles();
            var shortCandles = currentPair.getShortTickerCandles();

            if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
                log.warn("⚠️ Нет данных свечей для создания чарта пересечений");
                intersectionsChartImage.setSrc("");
                intersectionsChartImage.setAlt("Нет данных для чарта пересечений");
                return;
            }

            // Подсчитываем пересечения (простой алгоритм)
            int intersectionsCount = calculateIntersections(longCandles, shortCandles);

            log.info("📊 Найдено {} пересечений цен для пары {}", intersectionsCount, currentPair.getPairName());

            // Создаем чарт пересечений
            BufferedImage intersectionsChart = chartService.createNormalizedPriceIntersectionsChart(
                    longCandles, shortCandles, currentPair.getPairName(), intersectionsCount, false);

            if (intersectionsChart != null) {
                StreamResource intersectionsResource = createStreamResource(intersectionsChart, "intersections-chart.png");
                intersectionsChartImage.setSrc(intersectionsResource);
                intersectionsChartImage.setAlt("Intersections Chart for " + currentPair.getPairName());
                log.debug("✅ Чарт пересечений успешно создан");
            } else {
                intersectionsChartImage.setSrc("");
                intersectionsChartImage.setAlt("Failed to generate intersections chart");
                log.warn("⚠️ Не удалось создать чарт пересечений");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при создании чарта пересечений", e);
            intersectionsChartImage.setSrc("");
            intersectionsChartImage.setAlt("Chart generation error");
        }
    }

    /**
     * Подсчитывает количество пересечений между нормализованными ценами
     */
    private int calculateIntersections(java.util.List<com.example.shared.dto.Candle> longCandles,
                                       java.util.List<com.example.shared.dto.Candle> shortCandles) {
        try {
            int minSize = Math.min(longCandles.size(), shortCandles.size());
            if (minSize < 2) return 0;

            // Нормализуем цены
            double[] normalizedLongPrices = normalizePrices(longCandles, minSize);
            double[] normalizedShortPrices = normalizePrices(shortCandles, minSize);

            // Подсчитываем пересечения
            int intersections = 0;
            boolean firstAboveSecond = normalizedLongPrices[0] > normalizedShortPrices[0];

            for (int i = 1; i < minSize; i++) {
                boolean currentFirstAboveSecond = normalizedLongPrices[i] > normalizedShortPrices[i];
                if (currentFirstAboveSecond != firstAboveSecond) {
                    intersections++;
                    firstAboveSecond = currentFirstAboveSecond;
                }
            }

            return intersections;
        } catch (Exception e) {
            log.error("❌ Ошибка при подсчете пересечений: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Нормализует цены в диапазон [0, 1]
     */
    private double[] normalizePrices(java.util.List<com.example.shared.dto.Candle> candles, int size) {
        double[] prices = new double[size];
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        // Извлекаем цены закрытия и находим min/max
        for (int i = 0; i < size; i++) {
            prices[i] = candles.get(i).getClose();
            min = Math.min(min, prices[i]);
            max = Math.max(max, prices[i]);
        }

        // Нормализуем
        double range = max - min;
        if (range == 0) {
            return new double[size]; // Все цены одинаковые
        }

        for (int i = 0; i < size; i++) {
            prices[i] = (prices[i] - min) / range;
        }

        return prices;
    }

    /**
     * Обновляет панель с информацией о данных
     */
    private void updateDataInfoPanel(Pair tradingPair) {
        dataInfoPanel.removeAll();

        try {
            log.debug("📊 Обновление информационной панели для пары: {}", tradingPair.getPairName());

            VerticalLayout infoLayout = new VerticalLayout();
            infoLayout.setSpacing(false);
            infoLayout.setPadding(false);

            // Заголовок
            Span titleSpan = new Span("📊 Информация о данных");
            titleSpan.getStyle().set("font-weight", "bold");
            titleSpan.getStyle().set("font-size", "1.1rem");
            titleSpan.getStyle().set("color", "var(--lumo-primary-text-color)");
            titleSpan.getStyle().set("margin-bottom", "0.5rem");
            titleSpan.getStyle().set("display", "block");

            // Получаем настройки для ТФ
            Settings settings = settingsService.getSettings();
            String timeframe = settings != null ? settings.getTimeframe() : "N/A";
            String period = settings != null ? formatPeriod(settings.getCandleLimit(), timeframe) : "N/A";

            // Информация о Long тикере
            String longInfo = formatTickerInfo(
                    tradingPair.getLongTicker(),
                    tradingPair.getLongTickerCandles(),
                    timeframe,
                    period
            );

            // Информация о Short тикере
            String shortInfo = formatTickerInfo(
                    tradingPair.getShortTicker(),
                    tradingPair.getShortTickerCandles(),
                    timeframe,
                    period
            );

            // Создаем текстовые элементы
            Span longSpan = new Span(longInfo);
            longSpan.getStyle().set("display", "block");
            longSpan.getStyle().set("margin-bottom", "0.3rem");
            longSpan.getStyle().set("color", "#4CAF50"); // Зеленый для LONG

            Span shortSpan = new Span(shortInfo);
            shortSpan.getStyle().set("display", "block");
            shortSpan.getStyle().set("color", "#F44336"); // Красный для SHORT

            infoLayout.add(titleSpan, longSpan, shortSpan);
            dataInfoPanel.add(infoLayout);

            log.debug("✅ Информационная панель обновлена");
        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении информационной панели", e);
            Span errorSpan = new Span("Ошибка загрузки информации о данных");
            errorSpan.getStyle().set("color", "var(--lumo-error-text-color)");
            dataInfoPanel.add(errorSpan);
        }
    }

    /**
     * Форматирует информацию о тикере
     */
    private String formatTickerInfo(String ticker, java.util.List<com.example.shared.dto.Candle> candles,
                                    String timeframe, String period) {
        if (candles == null || candles.isEmpty()) {
            return String.format("%s: Нет данных", ticker);
        }

        try {
            // Сортируем свечи по времени для корректной работы с датами
            var sortedCandles = candles.stream()
                    .sorted(java.util.Comparator.comparing(com.example.shared.dto.Candle::getTimestamp))
                    .toList();

            int totalCandles = sortedCandles.size();
            long firstCandleTime = sortedCandles.get(0).getTimestamp();
            long lastCandleTime = sortedCandles.get(totalCandles - 1).getTimestamp();

            // Форматируем даты с учетом ТФ
            String datePattern = getDatePattern(timeframe);
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(datePattern);
            formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

            String firstDate = formatter.format(new java.util.Date(firstCandleTime));
            String lastDate = formatter.format(new java.util.Date(lastCandleTime));

            return String.format("%s: %s, %s, %d точек, с %s по %s",
                    ticker, timeframe, period, totalCandles, firstDate, lastDate);

        } catch (Exception e) {
            log.error("❌ Ошибка при форматировании информации о тикере {}: {}", ticker, e.getMessage());
            return String.format("%s: Ошибка обработки данных (%d свечей)", ticker, candles.size());
        }
    }

    /**
     * Возвращает паттерн форматирования даты в зависимости от ТФ
     */
    private String getDatePattern(String timeframe) {
        return switch (timeframe) {
            case "1m", "5m", "15m", "30m" -> "dd.MM.yyyy HH:mm";
            case "1H", "4H" -> "dd.MM.yyyy HH:mm";
            case "1d", "1D" -> "dd.MM.yyyy";
            default -> "dd.MM.yyyy HH:mm";
        };
    }

    /**
     * Форматирует период в читабельный вид на основе candleLimit и timeframe
     */
    private String formatPeriod(double candleLimit, String timeframe) {
        if (candleLimit <= 0 || timeframe == null) {
            return "N/A";
        }

        try {
            // Преобразуем candleLimit в количество дней в зависимости от timeframe
            double totalDays = switch (timeframe.toLowerCase()) {
                case "1m" -> candleLimit / (24 * 60);           // 1 минута = 1440 свечей в день
                case "5m" -> candleLimit / (24 * 12);           // 5 минут = 288 свечей в день
                case "15m" -> candleLimit / (24 * 4);           // 15 минут = 96 свечей в день
                case "30m" -> candleLimit / (24 * 2);           // 30 минут = 48 свечей в день
                case "1h" -> candleLimit / 24;                  // 1 час = 24 свечи в день
                case "4h" -> candleLimit / 6;                   // 4 часа = 6 свечей в день
                case "1d" -> candleLimit;                      // 1 день = 1 свеча в день
                default -> candleLimit;                         // По умолчанию как дни
            };

            int days = (int) Math.round(totalDays);

            if (days >= 365) {
                int years = days / 365;
                int remainingDays = days % 365;
                if (remainingDays == 0) {
                    return years == 1 ? "1 год" : years + " лет";
                } else {
                    return String.format("%d %s %d дн.", years, years == 1 ? "год" : "лет", remainingDays);
                }
            } else if (days >= 30) {
                int months = days / 30;
                int remainingDays = days % 30;
                if (remainingDays == 0) {
                    return months == 1 ? "1 месяц" : months + " мес.";
                } else {
                    return String.format("%d мес. %d дн.", months, remainingDays);
                }
            } else {
                return days == 1 ? "1 день" : days + " дней";
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при форматировании периода: {}", e.getMessage(), e);
            return String.format("%.0f свечей", candleLimit);
        }
    }

}