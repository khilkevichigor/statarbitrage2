package com.example.statarbitrage.ui.components;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.utils.ZScoreChart;
import com.example.statarbitrage.core.services.SettingsService;
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

    private final SettingsService settingsService;

    private VerticalLayout content;
    private Image chartImage;
    private H3 pairTitle;
    private Div detailsPanel;
    private Checkbox showEmaCheckbox;
    private Checkbox showStochRsiCheckbox;
    private PairData currentPairData;

    public ZScoreChartDialog(SettingsService settingsService) {
        this.settingsService = settingsService;
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

        // Создаем чекбоксы для дополнительных индикаторов
        createIndicatorCheckboxes();

        chartImage = new Image();
        chartImage.setWidth("100%");
        chartImage.setHeight("400px");
        chartImage.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        chartImage.getStyle().set("border-radius", "var(--lumo-border-radius-m)");

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
     * Создает чекбоксы для отображения дополнительных индикаторов
     */
    private void createIndicatorCheckboxes() {
        Settings settings = settingsService.getSettings();

        showEmaCheckbox = new Checkbox("Показать EMA (" + getEmaPeriodFromTimeframe(settings.getTimeframe()) + ")");
        showEmaCheckbox.setValue(false);
        showEmaCheckbox.addValueChangeListener(e -> refreshChart());

        showStochRsiCheckbox = new Checkbox("Отобразить StochRSI");
        showStochRsiCheckbox.setValue(false);
        showStochRsiCheckbox.addValueChangeListener(e -> refreshChart());
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
     * Обновляет чарт с учетом выбранных индикаторов
     */
    private void refreshChart() {
        if (currentPairData != null) {
            try {
                // Получаем настройки для индикаторов
                boolean showEma = showEmaCheckbox.getValue();
                boolean showStochRsi = showStochRsiCheckbox.getValue();

                // Генерируем новый чарт с выбранными индикаторами
                BufferedImage chartBufferedImage = generateEnhancedChart(currentPairData, showEma, showStochRsi);

                if (chartBufferedImage != null) {
                    StreamResource chartResource = createStreamResource(chartBufferedImage);
                    chartImage.setSrc(chartResource);
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при обновлении чарта", e);
            }
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

        // Панель с чекбоксами для индикаторов
        HorizontalLayout indicatorsPanel = new HorizontalLayout();
        indicatorsPanel.setWidthFull();
        indicatorsPanel.setAlignItems(FlexComponent.Alignment.CENTER);
        indicatorsPanel.getStyle().set("padding", "0.5rem");
        indicatorsPanel.getStyle().set("background", "var(--lumo-contrast-5pct)");
        indicatorsPanel.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        indicatorsPanel.getStyle().set("margin-bottom", "1rem");

        Span indicatorsLabel = new Span("📊 Индикаторы:");
        indicatorsLabel.getStyle().set("font-weight", "bold");
        indicatorsLabel.getStyle().set("margin-right", "1rem");

        indicatorsPanel.add(indicatorsLabel, showEmaCheckbox, showStochRsiCheckbox);

        content.add(header, indicatorsPanel, chartImage, detailsPanel);
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
            log.info("📊 Показываем Z-Score чарт для пары: {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker());

            // Сохраняем текущие данные пары
            this.currentPairData = pairData;

            // Устанавливаем заголовок
            pairTitle.setText(String.format("📊 Z-Score Chart: %s / %s",
                    pairData.getLongTicker(), pairData.getShortTicker()));

            // Сбрасываем состояние чекбоксов
            showEmaCheckbox.setValue(false);
            showStochRsiCheckbox.setValue(false);

            // Генерируем и показываем базовый чарт
            BufferedImage chartBufferedImage = pairData.getZScoreChartImage();

            if (chartBufferedImage != null) {
                StreamResource chartResource = createStreamResource(chartBufferedImage);
                chartImage.setSrc(chartResource);
                chartImage.setAlt("Z-Score Chart for " + pairData.getLongTicker() + " / " + pairData.getShortTicker());
            } else {
                // Fallback если чарт не удалось создать
                chartImage.setSrc(""); // Clear image
                chartImage.setAlt("Chart generation failed");
                log.warn("⚠️ Не удалось создать чарт для пары: {}/{}",
                        pairData.getLongTicker(), pairData.getShortTicker());
            }

            // Заполняем детальную информацию
            updateDetailsPanel(pairData);

            // Открываем диалог
            open();

        } catch (Exception e) {
            log.error("❌ Ошибка при показе чарта для пары: {}/{}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e);

            // Показываем ошибку пользователю
            pairTitle.setText("❌ Error Loading Chart");
            chartImage.setSrc("");
            detailsPanel.removeAll();
            detailsPanel.add(new Span("Failed to load chart: " + e.getMessage()));
            open();
        }
    }

    /**
     * Создает StreamResource из BufferedImage
     */
    private StreamResource createStreamResource(BufferedImage bufferedImage) {
        return new StreamResource("zscore-chart.png", () -> {
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

    /**
     * Генерирует расширенный чарт с дополнительными индикаторами
     */
    private BufferedImage generateEnhancedChart(PairData pairData, boolean showEma, boolean showStochRsi) {
        if (!showEma && !showStochRsi) {
            // Если ничего дополнительного не нужно показывать, возвращаем базовый чарт
            return pairData.getZScoreChartImage();
        }

        log.info("📊 Генерируем расширенный чарт с EMA: {}, StochRSI: {}", showEma, showStochRsi);

        try {
            Settings settings = settingsService.getSettings();
            int emaPeriod = getEmaPeriodFromTimeframe(settings.getTimeframe());

            // Используем новый API для создания расширенного чарта
            return ZScoreChart.createEnhancedBufferedImage(pairData, showEma, emaPeriod, showStochRsi);
        } catch (Exception e) {
            log.error("❌ Ошибка при создании расширенного чарта", e);
            return pairData.getZScoreChartImage();
        }
    }
}