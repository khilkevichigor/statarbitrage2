package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.ZScoreEntry;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public final class ZScoreChart {
    private static final String CHARTS_DIR = "charts";

    private ZScoreChart() {
    }

    public static void create(List<ZScoreEntry> zScoreEntries, PairData pairData) {
        if (zScoreEntries == null || zScoreEntries.isEmpty()) {
            log.warn("Список ZScoreEntry пуст — нечего рисовать.");
            return;
        }

        // 🔽 Добавь сортировку по времени
        zScoreEntries.sort(Comparator.comparingLong(ZScoreEntry::getTimestamp));

        // Извлекаем timestamps и zScores из списка zScoreEntries
        List<Long> timestamps = zScoreEntries.stream()
                .map(ZScoreEntry::getTimestamp)
                .collect(Collectors.toList());
        log.info("Временной диапазон графика: {} - {}", new Date(timestamps.get(0)), new Date(timestamps.get(timestamps.size() - 1)));

        List<Double> zScores = zScoreEntries.stream()
                .map(ZScoreEntry::getZscore)
                .collect(Collectors.toList());
        log.info("Первые 5 Z-значений: {}", zScores.stream().limit(5).collect(Collectors.toList()));
        log.info("Последние 5 Z-значений: {}", zScores.stream().skip(Math.max(0, zScores.size() - 5)).collect(Collectors.toList()));


        if (timestamps.size() != zScores.size()) {
            log.warn("Неверные входные данные для построения Z-графика");
            return;
        }

        List<Date> timeAxis = timestamps.stream().map(Date::new).collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Z-Score " + pairData.getA() + " - " + pairData.getB())
                .xAxisTitle("Time").yAxisTitle("Z-Score")
                .build();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDatePattern("HH:mm");
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);

        XYSeries zSeries = chart.addSeries("Z-Score", timeAxis, zScores);
        zSeries.setLineColor(Color.MAGENTA);
        zSeries.setMarker(new None());

        // Горизонтальные уровни
        addHorizontalLine(chart, timeAxis, 2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, 1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, 0.0, Color.BLACK);
        addHorizontalLine(chart, timeAxis, -1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, -2.0, Color.RED);

        // Вертикальная линия входа
        if (pairData.getEntryTime() > 0
                && pairData.getEntryTime() >= timestamps.get(0)
                && pairData.getEntryTime() <= timestamps.get(timestamps.size() - 1)) {

            OptionalInt indexOpt = findClosestIndex(timestamps, pairData.getEntryTime());

            // Рисуем линию и точку только если entryTime попадает в текущее окно данных
            if (indexOpt.isPresent()) {
                int index = indexOpt.getAsInt();

                Date entryDate = new Date(pairData.getEntryTime());
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
            }
        }

        // Сохраняем график
        String fileName = CHARTS_DIR + "/" + System.currentTimeMillis() + "_zscore.png";
        saveChart(chart, fileName);
    }

    public static void saveChart(XYChart chart, String filePath) {
        try {
            BitmapEncoder.saveBitmap(chart, filePath.replace(".png", ""), BitmapEncoder.BitmapFormat.PNG);
            log.info("График сохранён: {}", filePath);
        } catch (IOException e) {
            log.error("Ошибка при сохранении графика", e);
        }
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
