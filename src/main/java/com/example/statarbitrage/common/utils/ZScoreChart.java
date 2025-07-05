package com.example.statarbitrage.common.utils;

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
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.statarbitrage.common.constant.Constants.CHARTS_DIR;

@Slf4j
public final class ZScoreChart {

    private ZScoreChart() {
    }

    public static void create(PairData pairData) {
        XYChart chart = buildZScoreChart(pairData);

        // Сохраняем график
        String fileName = CHARTS_DIR + "/" + System.currentTimeMillis() + "_zscore.png";
        saveChart(chart, fileName);
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
        // Создаем простые данные для демонстрации (в реальности должна быть история)
        List<Long> timestamps = generateTimeStamps(30); // 30 точек данных
        List<Double> zScores = generateZScoreHistory(pairData.getZScoreCurrent(), 30);

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
        addHorizontalLine(chart, timeAxis, 2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, 1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, 0.0, Color.BLACK);
        addHorizontalLine(chart, timeAxis, -1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, -2.0, Color.RED);

        // Вертикальная линия входа (используем timestamp вместо entryTime)
        if (pairData.getTimestamp() > 0
                && pairData.getTimestamp() >= timestamps.get(0)
                && pairData.getTimestamp() <= timestamps.get(timestamps.size() - 1)) {

            OptionalInt indexOpt = findClosestIndex(timestamps, pairData.getTimestamp());

            // Рисуем линию и точку только если timestamp попадает в текущее окно данных
            if (indexOpt.isPresent()) {
                int index = indexOpt.getAsInt();

                Date entryDate = new Date(pairData.getTimestamp());
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

        return chart;
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

    /**
     * Генерация временных меток для демонстрационного графика
     */
    private static List<Long> generateTimeStamps(int count) {
        List<Long> timestamps = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        long interval = 60000; // 1 минута между точками

        for (int i = count - 1; i >= 0; i--) {
            timestamps.add(currentTime - (i * interval));
        }

        return timestamps;
    }

    /**
     * Генерация истории Z-Score для демонстрационного графика
     */
    private static List<Double> generateZScoreHistory(double currentZScore, int count) {
        List<Double> zScores = new ArrayList<>();
        Random random = new Random();

        // Создаем реалистичную историю, которая приводит к текущему значению
        double baseValue = 0.0;
        double volatility = 0.3;

        for (int i = 0; i < count - 1; i++) {
            // Постепенно приближаемся к текущему значению
            double progress = (double) i / (count - 1);
            double targetValue = currentZScore * progress;
            baseValue = targetValue + (random.nextGaussian() * volatility);
            zScores.add(baseValue);
        }

        // Последняя точка - точное текущее значение
        zScores.add(currentZScore);

        return zScores;
    }
}
