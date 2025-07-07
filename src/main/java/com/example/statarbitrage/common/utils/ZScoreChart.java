package com.example.statarbitrage.common.utils;

import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public final class ZScoreChart {

    private ZScoreChart() {
    }

    /**
     * Создает BufferedImage с Z-Score графиком для отображения в UI
     *
     * @param pairData данные торговой пары
     * @return BufferedImage с Z-Score графиком
     */
    public static BufferedImage createBufferedImage(PairData pairData) {
        log.info("Создание BufferedImage Z-Score графика для пары: {} / {}",
                pairData.getLongTicker(), pairData.getShortTicker());

        XYChart chart = buildZScoreChart(pairData);

        // Возвращаем BufferedImage для UI
        return BitmapEncoder.getBufferedImage(chart);
    }

    /**
     * Создает XYChart с Z-Score данными
     *
     * @param pairData данные торговой пары
     * @return настроенный XYChart
     */
    private static XYChart buildZScoreChart(PairData pairData) {
        // Получаем реальную историю Z-Score из PairData
        List<ZScoreParam> history = pairData.getZScoreHistory();

        List<Long> timestamps;
        List<Double> zScores;

        if (history.isEmpty()) {
            // Fallback: если истории нет, создаем минимальную точку с текущими данными
            log.warn("История Z-Score пуста для пары {}/{}, создаем минимальные данные",
                    pairData.getLongTicker(), pairData.getShortTicker());
            timestamps = Collections.singletonList(System.currentTimeMillis());
            zScores = Collections.singletonList(pairData.getZScoreCurrent());
        } else {
            // Используем реальную историю
            timestamps = history.stream()
                    .map(ZScoreParam::getTimestamp)
                    .collect(Collectors.toList());
            zScores = history.stream()
                    .map(ZScoreParam::getZscore)
                    .collect(Collectors.toList());

            log.info("Используем реальную историю Z-Score: {} точек для пары {}/{}",
                    history.size(), pairData.getLongTicker(), pairData.getShortTicker());
        }

        log.info("Временной диапазон графика от: {} - до: {}",
                new Date(timestamps.get(0)), new Date(timestamps.get(timestamps.size() - 1)));
        log.info("Текущий Z-Score: {}", pairData.getZScoreCurrent());

        if (timestamps.size() != zScores.size()) {
            log.warn("Неверные входные данные для построения Z-графика");
            throw new IllegalArgumentException("Timestamps and zScores lists must have the same size");
        }

        List<Date> timeAxis = timestamps.stream().map(Date::new).collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Z-Score LONG (" + pairData.getLongTicker() + ") - SHORT (" + pairData.getShortTicker() + ")")
                .xAxisTitle("Time").yAxisTitle("Z-Score")
                .build();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDatePattern("HH:mm");
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);

        XYSeries zSeries = chart.addSeries("Z-Score", timeAxis, zScores);
        zSeries.setLineColor(Color.MAGENTA);
        zSeries.setMarker(new None());

        // Горизонтальные уровни
        addHorizontalLine(chart, timeAxis, 3.0, Color.ORANGE);
        addHorizontalLine(chart, timeAxis, 2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, 1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, 0.0, Color.BLACK);
        addHorizontalLine(chart, timeAxis, -1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, -2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, -3.0, Color.ORANGE);

        // Вертикальная линия входа - используем entryTime (время создания трейда)
        long entryTimestamp = pairData.getEntryTime() > 0 ? pairData.getEntryTime() : pairData.getTimestamp();
        long historyStart = timestamps.get(0);
        long historyEnd = timestamps.get(timestamps.size() - 1);

        log.info("Проверка линии входа: entryTime={}, historyStart={}, historyEnd={}",
                new Date(entryTimestamp), new Date(historyStart), new Date(historyEnd));
        log.info("PairData: entryTime={}, timestamp={}", pairData.getEntryTime(), pairData.getTimestamp());

        // Проверяем попадает ли время входа в диапазон истории
        boolean inRange = entryTimestamp > 0 && entryTimestamp >= historyStart && entryTimestamp <= historyEnd;

        if (inRange) {
            log.info("Время входа попадает в диапазон истории - рисуем точную линию входа");

            OptionalInt indexOpt = findClosestIndex(timestamps, entryTimestamp);

            // Рисуем линию и точку только если timestamp попадает в текущее окно данных
            if (indexOpt.isPresent()) {
                int index = indexOpt.getAsInt();

                Date entryDate = new Date(entryTimestamp);
                List<Date> lineX = Arrays.asList(entryDate, entryDate);

                double minY = zScores.stream().min(Double::compareTo).orElse(-2.0);
                double maxY = zScores.stream().max(Double::compareTo).orElse(2.0);
                List<Double> lineY = Arrays.asList(minY, maxY);

                XYSeries entryLine = chart.addSeries("Entry", lineX, lineY);
                entryLine.setLineColor(Color.BLUE);
                entryLine.setMarker(new None());
                entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

                // Точка входа
                XYSeries entryPoint = chart.addSeries("Entry Point",
                        Collections.singletonList(timeAxis.get(index)),
                        Collections.singletonList(zScores.get(index)));
                entryPoint.setMarkerColor(Color.BLUE.darker());
                entryPoint.setLineColor(Color.BLUE.darker());
                entryPoint.setMarker(SeriesMarkers.CIRCLE);
                entryPoint.setLineStyle(new BasicStroke(0f));

                log.info("✅ Линия входа добавлена на графике в позиции {}", index);
            }
        } else if (entryTimestamp > 0) {
            log.warn("Время входа не попадает в диапазон истории - показываем приблизительную линию");

            // Показываем линию входа на ближайшей границе
            Date entryDate;
            int index;

            if (entryTimestamp < historyStart) {
                // Время входа раньше истории - показываем в начале
                entryDate = new Date(historyStart);
                index = 0;
                log.info("Показываем линию входа в начале графика");
            } else {
                // Время входа позже истории - показываем в конце
                entryDate = new Date(historyEnd);
                index = timestamps.size() - 1;
                log.info("Показываем линию входа в конце графика");
            }

            List<Date> lineX = Arrays.asList(entryDate, entryDate);
            double minY = zScores.stream().min(Double::compareTo).orElse(-2.0);
            double maxY = zScores.stream().max(Double::compareTo).orElse(2.0);
            List<Double> lineY = Arrays.asList(minY, maxY);

            XYSeries entryLine = chart.addSeries("Entry (approx)", lineX, lineY);
            entryLine.setLineColor(Color.ORANGE);
            entryLine.setMarker(new None());
            entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

            // Точка входа
            XYSeries entryPoint = chart.addSeries("Entry Point (approx)",
                    Collections.singletonList(timeAxis.get(index)),
                    Collections.singletonList(zScores.get(index)));
            entryPoint.setMarkerColor(Color.ORANGE.darker());
            entryPoint.setLineColor(Color.ORANGE.darker());
            entryPoint.setMarker(SeriesMarkers.CIRCLE);
            entryPoint.setLineStyle(new BasicStroke(0f));

            log.info("✅ Приблизительная линия входа добавлена на графике");
        } else {
            log.warn("Время входа не задано (0) - линия входа не будет показана");
        }

        return chart;
    }

    private static void addHorizontalLine(XYChart chart, List<Date> timeAxis, double yValue, Color color) {
        List<Double> yLine = Arrays.asList(yValue, yValue);
        XYSeries line = chart.addSeries("level_" + yValue, Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1)), yLine);
        line.setLineColor(color);
        line.setMarker(new None());
        line.setLineStyle(new BasicStroke(2.5f));
    }

    private static OptionalInt findClosestIndex(List<Long> timestamps, long targetTime) {
        for (int i = 0; i < timestamps.size(); i++) {
            if (timestamps.get(i) >= targetTime) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }
}
