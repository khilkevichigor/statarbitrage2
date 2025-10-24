package com.example.core.services.chart;

import com.example.shared.dto.ZScoreParam;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.None;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 🎯 Билдер для создания Z-Score чартов
 * Отвечает за базовое построение графиков Z-Score с точками входа
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZScoreChartBuilder {

    private final InterpolationService interpolationService;

    /**
     * 🎯 Создает базовый Z-Score чарт с опциональной точкой входа
     */
    public XYChart buildBasicZScoreChart(Pair tradingPair, boolean showEntryPoint) {
        List<ZScoreParam> history = tradingPair.getZScoreHistory();

        List<Long> timestamps;
        List<Double> zScores;

        if (history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста для пары {}, создаем минимальные данные",
                    tradingPair.getPairName());
            timestamps = Collections.singletonList(System.currentTimeMillis());
            zScores = Collections.singletonList(
                    tradingPair.getZScoreCurrent() != null ?
                            tradingPair.getZScoreCurrent().doubleValue() : 0.0);
        } else {
            timestamps = history.stream()
                    .map(ZScoreParam::getTimestamp)
                    .toList();
            zScores = history.stream()
                    .map(ZScoreParam::getZscore)
                    .toList();

            log.debug("📊 Используем реальную историю Z-Score: {} точек для пары {}",
                    history.size(), tradingPair.getPairName());
        }

        log.debug("📅 Временной диапазон графика от: {} - до: {}",
                new Date(timestamps.get(0)), new Date(timestamps.get(timestamps.size() - 1)));
        log.debug("🎯 Текущий Z-Score: {}", tradingPair.getZScoreCurrent());

        if (!ChartUtils.isValidChartData(timestamps, zScores)) {
            log.warn("⚠️ Неверные входные данные для построения Z-графика");
            throw new IllegalArgumentException("Timestamps and zScores lists must have the same size");
        }

        List<Date> timeAxis = timestamps.stream().map(Date::new).toList();

        XYChart chart = new XYChartBuilder()
                .width(ChartUtils.CHART_WIDTH)
                .height(ChartUtils.CHART_HEIGHT)
                .title("Z-Score LONG (" + tradingPair.getLongTicker() + ") - SHORT (" + tradingPair.getShortTicker() + ")")
                .xAxisTitle("").yAxisTitle("")
                .build();

        ChartUtils.applyUnifiedChartStyle(chart, timeAxis);

        // Добавляем основную Z-Score линию
        XYSeries zSeries = chart.addSeries("Z-Score", timeAxis, zScores);
        zSeries.setLineColor(ChartUtils.ZSCORE_COLOR);
        zSeries.setMarker(new None());

        // Добавляем стандартные горизонтальные линии
        ChartUtils.addZScoreHorizontalLines(chart, timeAxis);

        // Добавляем точку входа если требуется
        if (showEntryPoint) {
            addEntryPointToChart(chart, tradingPair, timeAxis, zScores);
        }

        log.debug("✅ Базовый Z-Score чарт создан для пары {}", tradingPair.getPairName());
        return chart;
    }

    /**
     * 🎯 Добавляет точку входа на Z-Score чарт
     */
    private void addEntryPointToChart(XYChart chart, Pair tradingPair, List<Date> timeAxis, List<Double> zScores) {
        long entryTimestamp = getEntryTimestamp(tradingPair);

        if (entryTimestamp <= 0) {
            log.debug("⚠️ Время входа не задано (0) - линия входа не будет показана");
            return;
        }

        long historyStart = timeAxis.get(0).getTime();
        long historyEnd = timeAxis.get(timeAxis.size() - 1).getTime();

        log.debug("🔍 Проверка линии входа: entryTime={}, historyStart={}, historyEnd={}",
                new Date(entryTimestamp), new Date(historyStart), new Date(historyEnd));

        boolean inRange = entryTimestamp >= historyStart && entryTimestamp <= historyEnd;

        if (inRange) {
            addExactEntryPoint(chart, tradingPair, timeAxis, zScores, entryTimestamp);
        } else {
            addApproximateEntryPoint(chart, tradingPair, timeAxis, zScores, entryTimestamp, historyStart, historyEnd);
        }
    }

    /**
     * 🎯 Добавляет точную линию входа (время входа попадает в диапазон истории)
     */
    private void addExactEntryPoint(XYChart chart, Pair tradingPair, List<Date> timeAxis,
                                    List<Double> zScores, long entryTimestamp) {
        log.debug("🎯 Время входа попадает в диапазон истории - рисуем точную линию входа");

        Date entryDate = new Date(entryTimestamp);
        addVerticalEntryLine(chart, entryDate, zScores, "Entry", ChartUtils.ENTRY_POINT_COLOR);
        addHorizontalEntryLine(chart, tradingPair, timeAxis, "Entry Z-Score", ChartUtils.ENTRY_POINT_COLOR);

        log.debug("✅ Точная линия входа добавлена на графике");
    }

    /**
     * 🎯 Добавляет приблизительную линию входа (время входа вне диапазона истории)
     */
    private void addApproximateEntryPoint(XYChart chart, Pair tradingPair, List<Date> timeAxis,
                                          List<Double> zScores, long entryTimestamp,
                                          long historyStart, long historyEnd) {
        log.debug("⚠️ Время входа не попадает в диапазон истории - показываем приблизительную линию");

        Date entryDate;
        if (entryTimestamp < historyStart) {
            entryDate = new Date(historyStart);
            log.debug("📍 Показываем линию входа в начале графика");
        } else {
            entryDate = new Date(historyEnd);
            log.debug("📍 Показываем линию входа в конце графика");
        }

        addVerticalEntryLine(chart, entryDate, zScores, "Entry (approx)", ChartUtils.ENTRY_POINT_APPROX_COLOR);
        addHorizontalEntryLine(chart, tradingPair, timeAxis, "Entry Z-Score (approx)", ChartUtils.ENTRY_POINT_APPROX_COLOR);

        log.debug("✅ Приблизительная линия входа добавлена на графике");
    }

    /**
     * 📏 Добавляет вертикальную линию входа
     */
    private void addVerticalEntryLine(XYChart chart, Date entryDate, List<Double> zScores,
                                      String seriesName, Color color) {
        List<Date> lineX = Arrays.asList(entryDate, entryDate);
        double minY = zScores.stream().min(Double::compareTo).orElse(-2.0);
        double maxY = zScores.stream().max(Double::compareTo).orElse(2.0);
        List<Double> lineY = Arrays.asList(minY, maxY);

        XYSeries entryLine = chart.addSeries(seriesName, lineX, lineY);
        entryLine.setLineColor(color);
        entryLine.setMarker(new None());
        entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                0, new float[]{6f, 4f}, 0));
    }

    /**
     * 📏 Добавляет горизонтальную линию Z-Score входа
     */
    private void addHorizontalEntryLine(XYChart chart, Pair tradingPair, List<Date> timeAxis,
                                        String seriesName, Color color) {
        if (tradingPair.getZScoreEntry() == null) {
            log.debug("⚠️ Z-Score входа не задан - пропускаем горизонтальную линию");
            return;
        }

        double entryZScore = tradingPair.getZScoreEntry().doubleValue();
        List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
        List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

        XYSeries entryHorizontalLine = chart.addSeries(seriesName, horizontalLineX, horizontalLineY);
        entryHorizontalLine.setLineColor(color);
        entryHorizontalLine.setMarker(new None());
        entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                0, new float[]{6f, 4f}, 0));
    }

    /**
     * 🕐 Получает таймштамп входа из данных торговой пары
     */
    private long getEntryTimestamp(Pair tradingPair) {
        if (tradingPair.getEntryTime() != null) {
            return tradingPair.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (tradingPair.getTimestamp() != null) {
            return tradingPair.getTimestamp();
        }
        return System.currentTimeMillis();
    }
}