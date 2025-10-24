package com.example.core.services.chart;

import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.None;

import java.awt.*;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.OptionalInt;

/**
 * 🛠️ Утилитный класс для работы с чартами
 * Содержит общие методы для создания и стилизации графиков
 */
@Slf4j
public final class ChartUtils {

    // Константы для унифицированного стиля чартов
    public static final int CHART_WIDTH = 1920;
    public static final int CHART_HEIGHT = 720;
    public static final int MAX_TIME_TICKS = 10;

    // Цвета для различных элементов графиков
    public static final Color ZSCORE_COLOR = Color.MAGENTA;
    public static final Color LONG_PRICE_COLOR = new Color(0, 255, 0, 120); // Полупрозрачный зеленый
    public static final Color SHORT_PRICE_COLOR = new Color(255, 0, 0, 120); // Полупрозрачный красный
    public static final Color EMA_COLOR = Color.CYAN;
    public static final Color STOCHRSI_COLOR = Color.ORANGE;
    public static final Color PROFIT_COLOR = Color.ORANGE;
    public static final Color PIXEL_SPREAD_COLOR = new Color(128, 0, 128, 150); // Полупрозрачный фиолетовый
    public static final Color ENTRY_POINT_COLOR = Color.BLUE;
    public static final Color ENTRY_POINT_APPROX_COLOR = Color.ORANGE;

    // Уровни горизонтальных линий Z-Score
    public static final double[] ZSCORE_LEVELS = {-3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0};
    public static final Color[] ZSCORE_LEVEL_COLORS = {
            Color.BLUE, Color.RED, Color.GRAY, Color.BLACK, Color.GRAY, Color.RED, Color.BLUE
    };

    private ChartUtils() {
        // Утилитный класс - приватный конструктор
    }

    /**
     * 🎨 Применяет унифицированный стиль ко всем чартам
     */
    public static void applyUnifiedChartStyle(XYChart chart, List<Date> timeAxis) {
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDatePattern(getOptimalDatePattern(timeAxis));
        chart.getStyler().setXAxisTickMarkSpacingHint(Math.max(50, timeAxis.size() / MAX_TIME_TICKS));
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setYAxisTicksVisible(false);
        chart.getStyler().setYAxisTitleVisible(false);
        chart.getStyler().setXAxisTitleVisible(false); // Убираем все подписи "Time"

        log.trace("🎨 Применен унифицированный стиль к чарту с {} временными точками", timeAxis.size());
    }

    /**
     * 📅 Определяет оптимальный паттерн отображения дат в зависимости от временного диапазона
     */
    public static String getOptimalDatePattern(List<Date> timeAxis) {
        if (timeAxis == null || timeAxis.size() < 2) {
            return "dd.MM HH:mm";
        }

        try {
            long startTime = timeAxis.get(0).getTime();
            long endTime = timeAxis.get(timeAxis.size() - 1).getTime();
            long durationMs = endTime - startTime;

            // Конвертируем в часы для удобства
            long durationHours = durationMs / (1000 * 60 * 60);

            log.debug("📅 Анализ временного диапазона: {} часов ({} - {})",
                    durationHours, timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));

            // Выбираем паттерн в зависимости от продолжительности
            if (durationHours <= 24) {
                // Меньше суток - показываем часы и минуты
                return "HH:mm";
            } else if (durationHours <= 24 * 7) {
                // Неделя - показываем день и время
                return "dd.MM HH:mm";
            } else if (durationHours <= 24 * 30) {
                // Месяц - показываем день и месяц
                return "dd.MM";
            } else if (durationHours <= 24 * 365) {
                // Год - показываем день и месяц
                return "dd.MM";
            } else {
                // Больше года - показываем месяц и год
                return "MM.yyyy";
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при определении паттерна даты: {}", e.getMessage(), e);
            return "dd.MM HH:mm"; // Паттерн по умолчанию
        }
    }

    /**
     * 📏 Добавляет горизонтальную линию на чарт
     */
    public static void addHorizontalLine(XYChart chart, List<Date> timeAxis, double yValue, Color color) {
        if (timeAxis.isEmpty()) {
            log.warn("⚠️ Пустая временная ось - пропускаем горизонтальную линию {}", yValue);
            return;
        }

        List<Double> yLine = Arrays.asList(yValue, yValue);
        XYSeries line = chart.addSeries("level_" + yValue,
                Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1)), yLine);
        line.setLineColor(color);
        line.setMarker(new None());
        line.setLineStyle(new BasicStroke(2.5f));

        log.trace("📏 Добавлена горизонтальная линия на уровне {}", yValue);
    }

    /**
     * 📊 Добавляет все стандартные горизонтальные линии Z-Score
     */
    public static void addZScoreHorizontalLines(XYChart chart, List<Date> timeAxis) {
        for (int i = 0; i < ZSCORE_LEVELS.length; i++) {
            addHorizontalLine(chart, timeAxis, ZSCORE_LEVELS[i], ZSCORE_LEVEL_COLORS[i]);
        }
        log.debug("📊 Добавлены все стандартные линии Z-Score уровней");
    }

    /**
     * 🔍 Находит ближайший индекс по таймштампу
     */
    public static OptionalInt findClosestIndex(List<Long> timestamps, long targetTimestamp) {
        if (timestamps.isEmpty()) {
            return OptionalInt.empty();
        }

        int bestIndex = 0;
        long bestDiff = Math.abs(timestamps.get(0) - targetTimestamp);

        for (int i = 1; i < timestamps.size(); i++) {
            long diff = Math.abs(timestamps.get(i) - targetTimestamp);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIndex = i;
            }
        }

        log.trace("🔍 Найден ближайший индекс {} для таймштампа {}", bestIndex, new Date(targetTimestamp));
        return OptionalInt.of(bestIndex);
    }

    /**
     * 📐 Нормализует значения в заданный диапазон
     */
    public static List<Double> normalizeValues(List<Double> values, double targetMin, double targetMax) {
        if (values.isEmpty()) {
            log.warn("⚠️ Пустой список значений для нормализации");
            return values;
        }

        double sourceMin = values.stream().min(Double::compareTo).orElse(0.0);
        double sourceMax = values.stream().max(Double::compareTo).orElse(1.0);
        double sourceRange = sourceMax - sourceMin;
        double targetRange = targetMax - targetMin;

        if (sourceRange == 0) {
            log.debug("📐 Источник имеет нулевой диапазон - возвращаем средний уровень целевого диапазона");
            double midValue = targetMin + targetRange / 2;
            return values.stream().map(v -> midValue).toList();
        }

        List<Double> normalized = values.stream()
                .map(value -> targetMin + ((value - sourceMin) / sourceRange) * targetRange)
                .toList();

        log.debug("📐 Нормализованы {} значений из диапазона [{}, {}] в [{}, {}]",
                values.size(), sourceMin, sourceMax, targetMin, targetMax);

        return normalized;
    }

    /**
     * 🎯 Конвертирует значение в пиксели для пиксельного спреда
     */
    public static double convertValueToPixel(double value, double minValue, double maxValue, int chartHeight) {
        if (maxValue - minValue == 0) {
            return chartHeight / 2.0;
        }

        // Нормализуем значение в диапазон [0, 1]
        double normalized = (value - minValue) / (maxValue - minValue);

        // Конвертируем в пиксели (Y=0 вверху, Y=chartHeight внизу)
        return chartHeight - (normalized * chartHeight);
    }

    /**
     * 🔢 Безопасное получение размера списка
     */
    public static int safeListSize(List<?> list) {
        return list != null ? list.size() : 0;
    }

    /**
     * ✅ Проверяет валидность данных для построения чарта
     */
    public static boolean isValidChartData(List<?> timeAxis, List<?> values) {
        if (timeAxis == null || values == null || timeAxis.isEmpty() || values.isEmpty()) {
            log.warn("⚠️ Невалидные данные для чарта: timeAxis={}, values={}",
                    safeListSize(timeAxis), safeListSize(values));
            return false;
        }

        if (timeAxis.size() != values.size()) {
            log.warn("⚠️ Размеры временной оси и значений не совпадают: {} vs {}",
                    timeAxis.size(), values.size());
            return false;
        }

        return true;
    }
}