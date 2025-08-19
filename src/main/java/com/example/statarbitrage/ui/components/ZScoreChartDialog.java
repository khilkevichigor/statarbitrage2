package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.common.model.ChartSettings;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.core.services.ChartSettingsService;
import com.example.statarbitrage.core.services.PixelSpreadService;
import com.example.statarbitrage.core.services.SettingsService;
import com.example.statarbitrage.ui.services.ChartService;
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
    private PairData currentPairData;

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
            case "1h" -> 14;
            case "4h" -> 12;
            case "1d" -> 10;
            default -> 14;
        };
    }

    /**
     * Обновляет главный чарт с учетом выбранных типов
     */
    private void refreshMainChart() {
        if (currentPairData == null) return;

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

                chartImage = chartService.createZScoreChart(currentPairData, showEma, emaPeriod, showStochRsi, showProfit, false, false, showEntryPoint);
                log.debug("📊 Создан Z-Score чарт с индикаторами: EMA={}, StochRSI={}, Profit={}", showEma, showStochRsi, showProfit);

            } else if (showCombinedPrice && !showZScore && !showPixelSpread) {
                // Только Price чарт с профитом
                boolean showProfit = showProfitCheckbox.getValue();
                chartImage = chartService.createPriceChartWithProfit(currentPairData, false, showProfit, showEntryPoint);
                log.debug("📊 Создан Price чарт с Profit={}", showProfit);

            } else if (showPixelSpread && !showZScore && !showCombinedPrice) {
                // Только Pixel Spread чарт с профитом
                boolean showProfit = showProfitCheckbox.getValue();
                chartImage = chartService.createPixelSpreadChartWithProfit(currentPairData, showProfit, showEntryPoint);
                log.debug("📊 Создан Pixel Spread чарт с Profit={}", showProfit);

            } else {
                // Комбинированный чарт - создаем комбинированный Z-Score чарт
                Settings settings = settingsService.getSettings();
                boolean showEma = showEmaCheckbox.getValue();
                boolean showStochRsi = showStochRsiCheckbox.getValue();
                boolean showProfit = showProfitCheckbox.getValue();
                int emaPeriod = getEmaPeriodFromTimeframe(settings.getTimeframe());

                chartImage = chartService.createCombinedChart(currentPairData, showZScore, showCombinedPrice, showPixelSpread, showEma, emaPeriod, showStochRsi, showProfit, showEntryPoint);
                log.debug("📊 Создан комбинированный чарт: ZScore={}, Price={}, PixelSpread={}", showZScore, showCombinedPrice, showPixelSpread);
            }

            if (chartImage != null) {
                StreamResource chartResource = createStreamResource(chartImage, "main-chart.png");
                mainChartImage.setSrc(chartResource);
                mainChartImage.setAlt("Chart for " + currentPairData.getPairName());
            } else {
                mainChartImage.setSrc("");
                mainChartImage.setAlt("Failed to generate chart");
                log.warn("⚠️ Не удалось создать чарт для пары: {}", currentPairData.getPairName());
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

        content.add(header, chartSelectionPanel, mainChartImage, detailsPanel);
        add(content);
    }

    /**
     * Показать Z-Score чарт для торговой пары
     *
     * @param pairData данные торговой пары
     */
    public void showChart(PairData pairData) {
        if (pairData == null) {
            log.warn("⚠️ Попытка показать чарт для null PairData");
            return;
        }

        try {
            log.debug("📊 Показываем Z-Score чарт для пары: {}", pairData.getPairName());

            // Сохраняем текущие данные пары
            this.currentPairData = pairData;

            // Устанавливаем заголовок
            pairTitle.setText(String.format("📊 Z-Score Chart: %s", pairData.getPairName()));

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
            pixelSpreadService.calculatePixelSpreadIfNeeded(currentPairData);

            // Генерируем и показываем чарт согласно выбранным чекбоксам
            refreshMainChart();

            // Заполняем детальную информацию
            updateDetailsPanel(pairData);

            // Открываем диалог
            open();

        } catch (Exception e) {
            log.error("❌ Ошибка при показе чарта для пары: {}", pairData.getPairName(), e);

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
    private void updateDetailsPanel(PairData pairData) {
        detailsPanel.removeAll();

        // Создаем HTML-контент с детальной информацией
        VerticalLayout details = new VerticalLayout();
        details.setSpacing(false);
        details.setPadding(false);

        // Основные метрики
        HorizontalLayout metricsRow1 = new HorizontalLayout();
        metricsRow1.setWidthFull();

        Div zScoreInfo = createMetricDiv("Current Z-Score",
                String.format("%.3f", pairData.getZScoreCurrent()),
                getZScoreColor(pairData.getZScoreCurrent()));

        Div correlationInfo = createMetricDiv("Correlation",
                String.format("%.3f", pairData.getCorrelationCurrent()), "#2196F3");

        Div statusInfo = createMetricDiv("Status",
                pairData.getStatus().toString(), "#4CAF50");

        metricsRow1.add(zScoreInfo, correlationInfo, statusInfo);

        // Вторая строка метрик (если есть данные о прибыли)
        HorizontalLayout metricsRow2 = new HorizontalLayout();
        metricsRow2.setWidthFull();

        if (pairData.getProfitPercentChanges() != null) {
            Div profitInfo = createMetricDiv("Profit",
                    pairData.getProfitPercentChanges().setScale(2, RoundingMode.HALF_UP) + "%",
                    getProfitColor(pairData.getProfitPercentChanges().doubleValue()));
            metricsRow2.add(profitInfo);
        }

        if (pairData.getMaxZ() != null && pairData.getMinZ() != null) {
            Div maxZInfo = createMetricDiv("Max Z",
                    pairData.getMaxZ().setScale(3, RoundingMode.HALF_UP).toString(), "#FF9800");
            Div minZInfo = createMetricDiv("Min Z",
                    pairData.getMinZ().setScale(3, RoundingMode.HALF_UP).toString(), "#FF9800");
            metricsRow2.add(maxZInfo, minZInfo);
        }

        details.add(metricsRow1);
        if (metricsRow2.getComponentCount() > 0) {
            details.add(metricsRow2);
        }

        // Trading recommendations
        Div recommendationDiv = createRecommendationDiv(pairData.getZScoreCurrent());
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

}