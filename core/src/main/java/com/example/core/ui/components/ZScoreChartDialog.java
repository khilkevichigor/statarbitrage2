package com.example.core.ui.components;

import com.example.core.services.SettingsService;
import com.example.core.services.chart.ChartService;
import com.example.core.services.chart.ChartSettingsService;
import com.example.core.services.chart.PixelSpreadService;
import com.example.shared.models.ChartSettings;
import com.example.shared.models.Pair;
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
    private Div dataInfoPanel; // Панель с информацией о данных
    private H3 pairTitle;
    private Div detailsPanel;
    // 🎯 УПРОЩЕНИЕ: Убираем ВСЕ чекбоксы! Только один для точек входа
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
        setWidth("98vw");
        setHeight("95vh");
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

        // Единая область для чартов с crosshair
        mainChartImage = new Image();
        mainChartImage.setWidth("100%");
        mainChartImage.setHeight("800px");
        mainChartImage.getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
        mainChartImage.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
        mainChartImage.getStyle().set("cursor", "crosshair");


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
     * 🎯 УПРОЩЕНИЕ: Создает ЕДИНСТВЕННЫЙ чекбокс для точек входа
     * Все остальные секции отображаются ВСЕГДА!
     */
    private void createChartSelectionCheckboxes() {
        // Загружаем сохраненные настройки чарта
        ChartSettings chartSettings = chartSettingsService.getChartSettings(CHART_TYPE);

        // 🎯 УПРОЩЕНИЕ: Только один чекбокс для точек входа
        showEntryPointCheckbox = new Checkbox("🎯 Показать точки входа");
        showEntryPointCheckbox.setValue(chartSettings.isShowEntryPoint());
        showEntryPointCheckbox.addValueChangeListener(e -> {
            chartSettingsService.updateChartSetting(CHART_TYPE, "showEntryPoint", e.getValue());
            refreshMainChart();
        });

        log.debug("🎯 УПРОЩЕНИЕ: Загружена настройка единственного чекбокса: EntryPoint={}",
                chartSettings.isShowEntryPoint());
    }

    // Удален метод getEmaPeriodFromTimeframe - больше не нужен

    /**
     * 🏗️ УПРОЩЁННОЕ обновление главного чарта
     * 🎯 УПРОЩЕНИЕ: Все секции отображаются ВСЕГДА! Только EntryPoint можно включать/отключать!
     */
    private void refreshMainChart() {
        if (currentPair == null) return;

        try {
            // 🎯 УПРОЩЕНИЕ: Получаем состояние ЕДИНСТВЕННОГО чекбокса
            boolean showEntryPoint = showEntryPointCheckbox.getValue();

            log.debug("🏗️ УПРОЩЁННОЕ создание ПОЛНОГО вертикального чарта (EntryPoint: {})",
                    showEntryPoint);

            // Используем УПРОЩЁННЫЙ метод createVerticalChart с ЕДИНСТВЕННЫМ параметром
            BufferedImage chartImage = chartService.createVerticalChart(currentPair, showEntryPoint);

            if (chartImage != null) {
                StreamResource chartResource = createStreamResource(chartImage, "vertical-chart.png");
                mainChartImage.setSrc(chartResource);
                mainChartImage.setAlt("Vertical Chart for " + currentPair.getPairName());

                // Добавляем crosshair функциональность
                addCrosshairToMainChart();

                log.debug("✅ Вертикальный чарт успешно создан и отображен");
            } else {
                mainChartImage.setSrc("");
                mainChartImage.setAlt("Failed to generate vertical chart");
                log.warn("⚠️ Не удалось создать вертикальный чарт для пары: {}", currentPair.getPairName());
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении вертикального чарта", e);
            mainChartImage.setSrc("");
            mainChartImage.setAlt("Vertical chart generation error");
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
        // 🎯 УПРОЩЕНИЕ: Только один чекбокс для точек входа
        mainChartsRow.add(showEntryPointCheckbox);

        // Добавляем пояснительный текст
        Span infoSpan = new Span("💡 Все секции (цены, Z-Score, пиксельный спред, профит) отображаются всегда");
        infoSpan.getStyle().set("font-size", "0.9rem");
        infoSpan.getStyle().set("color", "var(--lumo-secondary-text-color)");
        infoSpan.getStyle().set("font-style", "italic");
        infoSpan.getStyle().set("margin-top", "0.5rem");

        // Индикаторы EMA и StochRSI убраны для упрощения

        chartSelectionPanel.add(chartsLabel, mainChartsRow, infoSpan);


        content.add(header, dataInfoPanel, chartSelectionPanel, mainChartImage, detailsPanel);
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
            pairTitle.setText(String.format("📊 Z-Score Chart: %s %s", tradingPair.getPairName(), tradingPair.getUuid().toString()));

            // 🎯 УПРОЩЕНИЕ: Загружаем настройку ЕДИНСТВЕННОГО чекбокса из базы данных
            ChartSettings chartSettings = chartSettingsService.getChartSettings(CHART_TYPE);
            showEntryPointCheckbox.setValue(chartSettings.isShowEntryPoint());

            // 🎯 УПРОЩЕНИЕ: Все секции (цены, Z-Score, пиксельный спред, профит) отображаются ВСЕГДА!
            // Только точки входа можно включать/отключать через единственный чекбокс

            log.debug("🎯 УПРОЩЕНИЕ: Восстановлена настройка единственного чекбокса: EntryPoint={}",
                    chartSettings.isShowEntryPoint());

            // Вычисляем пиксельный спред независимо от чекбокса объединенных цен используя PixelSpreadService
            pixelSpreadService.calculatePixelSpreadIfNeeded(currentPair);

            // Генерируем и показываем чарт согласно выбранным чекбоксам
            refreshMainChart();


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

            // Информация о Long тикере (используем реальные данные свечей)
            String longInfo = formatRealTickerInfo(
                    tradingPair.getLongTicker(),
                    tradingPair.getLongTickerCandles()
            );

            // Информация о Short тикере (используем реальные данные свечей)
            String shortInfo = formatRealTickerInfo(
                    tradingPair.getShortTicker(),
                    tradingPair.getShortTickerCandles()
            );

            // Выравниваем тикеры по двоеточию
            java.util.List<String> alignedTickerInfos = alignTickersByColon(
                    java.util.List.of(longInfo, shortInfo)
            );
            String alignedLongInfo = alignedTickerInfos.get(0);
            String alignedShortInfo = alignedTickerInfos.get(1);

            // Информация о пересечениях отключена
            String intersectionInfo = "Пересечений: отключено";

            // Создаем текстовые элементы
            Span longSpan = new Span(alignedLongInfo);
            longSpan.getStyle().set("display", "block");
            longSpan.getStyle().set("margin-bottom", "0.3rem");
            longSpan.getStyle().set("color", "#4CAF50"); // Зеленый для LONG
            longSpan.getStyle().set("font-family", "monospace");

            Span shortSpan = new Span(alignedShortInfo);
            shortSpan.getStyle().set("display", "block");
            shortSpan.getStyle().set("margin-bottom", "0.5rem"); // Больший отступ перед пересечениями
            shortSpan.getStyle().set("color", "#F44336"); // Красный для SHORT
            shortSpan.getStyle().set("font-family", "monospace");

            // Информация о пересечениях
            Span intersectionSpan = new Span(intersectionInfo);
            intersectionSpan.getStyle().set("display", "block");
            intersectionSpan.getStyle().set("color", "#2196F3"); // Синий для пересечений
            intersectionSpan.getStyle().set("font-weight", "bold");
            intersectionSpan.getStyle().set("font-family", "monospace");

            infoLayout.add(titleSpan, longSpan, shortSpan, intersectionSpan);
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
     * Выравнивает тикеры по двоеточию, добавляя пробелы перед двоеточием
     */
    private java.util.List<String> alignTickersByColon(java.util.List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return tickers;
        }

        // Находим максимальную длину тикера (до двоеточия)
        int maxTickerLength = tickers.stream()
                .mapToInt(info -> {
                    int colonIndex = info.indexOf(':');
                    return colonIndex > 0 ? colonIndex : info.length();
                })
                .max()
                .orElse(0);

        // Выравниваем каждую строку
        return tickers.stream()
                .map(info -> {
                    int colonIndex = info.indexOf(':');
                    if (colonIndex > 0) {
                        String tickerPart = info.substring(0, colonIndex);
                        String restPart = info.substring(colonIndex);

                        // Добавляем пробелы для выравнивания
                        int spacesToAdd = maxTickerLength - tickerPart.length();
                        String padding = " ".repeat(Math.max(0, spacesToAdd));

                        return tickerPart + padding + restPart;
                    } else {
                        return info; // Если нет двоеточия, возвращаем как есть
                    }
                })
                .toList();
    }

    /**
     * Форматирует реальную информацию о тикере на основе фактических данных свечей
     */
    private String formatRealTickerInfo(String ticker, java.util.List<com.example.shared.dto.Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return String.format("%s: Нет данных", ticker);
        }

        try {
            // Сортируем свечи по времени
            var sortedCandles = candles.stream()
                    .sorted(java.util.Comparator.comparing(com.example.shared.dto.Candle::getTimestamp))
                    .toList();

            int totalCandles = sortedCandles.size();
            long firstCandleTime = sortedCandles.get(0).getTimestamp();
            long lastCandleTime = sortedCandles.get(totalCandles - 1).getTimestamp();

            // Определяем реальный ТФ на основе разности времени между свечами
            String realTimeframe = "N/A";
            String realPeriod = "N/A";

            if (totalCandles >= 2) {
                long timeDiffMs = sortedCandles.get(1).getTimestamp() - sortedCandles.get(0).getTimestamp();
                realTimeframe = determineTimeframeFromDiff(timeDiffMs);
                realPeriod = calculateRealPeriod(firstCandleTime, lastCandleTime);
            }

            // Форматируем даты
            String datePattern = getRealDatePattern(realTimeframe);
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(datePattern);
            formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

            String firstDate = formatter.format(new java.util.Date(firstCandleTime));
            String lastDate = formatter.format(new java.util.Date(lastCandleTime));

            return String.format("%s: %s, %s, %d точек, с %s по %s",
                    ticker, realTimeframe, realPeriod, totalCandles, firstDate, lastDate);

        } catch (Exception e) {
            log.error("❌ Ошибка при форматировании реальной информации о тикере {}: {}", ticker, e.getMessage());
            return String.format("%s: Ошибка обработки (%d свечей)", ticker, candles.size());
        }
    }

    /**
     * Определяет таймфрейм на основе разности времени между свечами
     */
    private String determineTimeframeFromDiff(long timeDiffMs) {
        long minutes = timeDiffMs / (1000 * 60);

        if (minutes == 1) return "1m";
        else if (minutes == 5) return "5m";
        else if (minutes == 15) return "15m";
        else if (minutes == 30) return "30m";
        else if (minutes == 60) return "1H";
        else if (minutes == 240) return "4H";
        else if (minutes == 1440) return "1D";
        else return minutes + "m";
    }

    /**
     * Вычисляет реальный период на основе временного диапазона
     */
    private String calculateRealPeriod(long startTime, long endTime) {
        long durationMs = endTime - startTime;
        long days = durationMs / (1000 * 60 * 60 * 24);

        if (days >= 365) {
            int years = (int) (days / 365);
            int remainingDays = (int) (days % 365);
            if (remainingDays == 0) {
                return years == 1 ? "1 год" : years + " лет";
            } else {
                return String.format("%d %s %d дн.", years, years == 1 ? "год" : "лет", remainingDays);
            }
        } else if (days >= 30) {
            int months = (int) (days / 30);
            int remainingDays = (int) (days % 30);
            if (remainingDays == 0) {
                return months == 1 ? "1 месяц" : months + " мес.";
            } else {
                return String.format("%d мес. %d дн.", months, remainingDays);
            }
        } else {
            return days == 1 ? "1 день" : days + " дней";
        }
    }

    /**
     * Определяет паттерн даты для реального таймфрейма
     */
    private String getRealDatePattern(String timeframe) {
        return switch (timeframe) {
            case "1m", "5m", "15m", "30m", "1H", "4H" -> "dd.MM.yyyy HH:mm";
            case "1D" -> "dd.MM.yyyy";
            default -> "dd.MM.yyyy HH:mm";
        };
    }

    /**
     * Форматирует информацию о тикере (устаревший метод)
     */
    @Deprecated
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
     * Возвращает паттерн форматирования даты в зависимости от ТФ (устаревший метод)
     */
    @Deprecated
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

    /**
     * Добавляет crosshair функциональность к главному вертикальному чарту
     * 🎯 ТОЛЬКО вертикальные линии! Горизонтальные линии только для Z-Score секции!
     */
    private void addCrosshairToMainChart() {
        mainChartImage.getElement().executeJs("""
                // Создаем контейнер с relative позиционированием для crosshair
                const imageContainer = document.createElement('div');
                imageContainer.style.position = 'relative';
                imageContainer.style.display = 'inline-block';
                imageContainer.style.width = '100%';
                imageContainer.style.height = '100%';
                
                // Переносим image в контейнер
                const img = this;
                const parent = img.parentNode;
                parent.insertBefore(imageContainer, img);
                imageContainer.appendChild(img);
                
                // Создаем ТОЛЬКО вертикальную линию crosshair
                const verticalLine = document.createElement('div');
                verticalLine.style.position = 'absolute';
                verticalLine.style.top = '0';
                verticalLine.style.width = '1px';
                verticalLine.style.height = '100%';
                verticalLine.style.backgroundColor = '#FF6B6B';
                verticalLine.style.pointerEvents = 'none';
                verticalLine.style.display = 'none';
                verticalLine.style.zIndex = '1000';
                imageContainer.appendChild(verticalLine);
                
                // 🎯 Горизонтальные линии НЕ создаем - только для Z-Score секции!
                
                // Обработчики событий мыши для crosshair
                img.addEventListener('mouseenter', function() {
                    verticalLine.style.display = 'block';
                    // 🎯 Горизонтальные линии НЕ показываем - только для Z-Score секции!
                });
                
                img.addEventListener('mouseleave', function() {
                    verticalLine.style.display = 'none';
                    // 🎯 Горизонтальные линии НЕ скрываем - только для Z-Score секции!
                });
                
                img.addEventListener('mousemove', function(e) {
                    const rect = img.getBoundingClientRect();
                    const x = e.clientX - rect.left;
                
                    verticalLine.style.left = x + 'px';
                    // 🎯 Горизонтальные линии НЕ перемещаем - только для Z-Score секции!
                });
                
                img.style.cursor = 'crosshair';
                """);
    }

}