package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ZScoreEntry;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public final class ComboTwoTrendsAndSpreadChart {
    private static final String CHARTS_DIR = "charts";

    private ComboTwoTrendsAndSpreadChart() {
    }

    public static void create(ConcurrentHashMap<String, List<Candle>> candlesMap,
                              ZScoreEntry bestPair, EntryData entryData) {
        String longTicker = bestPair.getA();
        String shortTicker = bestPair.getB();

        List<Candle> longCandles = candlesMap.get(longTicker);
        List<Candle> shortCandles = candlesMap.get(shortTicker);

        if (longCandles == null || shortCandles == null) {
            log.warn("Не найдены свечи для тикеров {} или {}", longTicker, shortTicker);
            return;
        }

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Получение времени и цен
        List<Date> timeAxis = longCandles.stream()
                .map(c -> new Date(c.getTimestamp()))
                .collect(Collectors.toList());

        List<Double> longPrices = longCandles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).collect(Collectors.toList());

        // Нормализуем цены
        List<Double> normLong = ChartUtil.normalize(longPrices);
        List<Double> normShort = ChartUtil.normalize(shortPrices);

        // Расчёт спреда
        // OLS-регрессия: long = β * short + ε
        double alpha = bestPair.getAlpha(); // нужно добавить в класс bestPair
        double beta = bestPair.getBeta();

        List<Double> spread = new ArrayList<>();
        for (int i = 0; i < longPrices.size(); i++) {
            // Используем формулу: spread = b - (beta * a + alpha)
            double value = longPrices.get(i) - (beta * shortPrices.get(i) + alpha);
            spread.add(value);
        }

        double mean = bestPair.getMean();
        double std = bestPair.getStd();

        // Создаём верхний график — нормализованные цены
        XYChart topChart = new XYChartBuilder()
                .width(1920).height(720)
                .title(ChartUtil.getTitle(entryData))
                .xAxisTitle("")  // убираем подпись "Time"
                .yAxisTitle("Normalized Price")
                .build();

        topChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);  // легенда сверху слева
        // Сделаем фон легенды полупрозрачным (например, белый с 50% прозрачности)
//        topChart.getStyler().setLegendBackgroundColor(new Color(255, 255, 255, 128)); // 128 из 255 = 50% прозрачности
        // Если хотите убрать рамку вокруг легенды или сделать ее тоже прозрачной
//        topChart.getStyler().setLegendBorderColor(new Color(0, 0, 0, 0)); // полностью прозрачная рамка
        topChart.getStyler().setXAxisTicksVisible(false);
        topChart.getStyler().setYAxisTicksVisible(false);
        topChart.getStyler().setYAxisTitleVisible(false);

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker + " (current " + entryData.getATickerCurrentPrice() + ")", timeAxis, normLong);
        longSeries.setLineColor(java.awt.Color.GREEN);
        longSeries.setMarker(new None());

        XYSeries shortSeries = topChart.addSeries("SHORT: " + shortTicker + " (current " + entryData.getBTickerCurrentPrice() + ")", timeAxis, normShort);
        shortSeries.setLineColor(java.awt.Color.RED);
        shortSeries.setMarker(new None());

        // Вертикальная линия по entryTime, если есть
        if (entryData.getEntryTime() > 0) {
            Date entryDate = new Date(entryData.getEntryTime());
            List<Date> lineX = Arrays.asList(entryDate, entryDate);
            List<Double> lineY = Arrays.asList(
                    Math.min(Collections.min(normLong), Collections.min(normShort)),
                    Math.max(Collections.max(normLong), Collections.max(normShort))
            );
            XYSeries entryLine = topChart.addSeries("Entry Point", lineX, lineY);
            entryLine.setLineColor(java.awt.Color.BLUE);
            entryLine.setMarker(new None());
            entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
        }

        //точки
        if (entryData.getEntryTime() > 0) {
            long entryTime = entryData.getEntryTime();

            // Найти ближайший индекс к entryTime
            int index = 0;
            for (int i = 0; i < timeAxis.size(); i++) {
                if (timeAxis.get(i).getTime() >= entryTime) {
                    index = i;
                    break;
                }
            }

            Date entryDate = timeAxis.get(index);

            // Добавляем точки на график
            XYSeries longEntryPoint = topChart.addSeries("Long Entry (" + entryData.getATickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(normLong.get(index)));
            longEntryPoint.setMarkerColor(Color.GREEN.darker());
            longEntryPoint.setLineColor(Color.GREEN.darker());
            longEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            longEntryPoint.setLineStyle(new BasicStroke(0f)); // линия не рисуется

            XYSeries shortEntryPoint = topChart.addSeries("Short Entry (" + entryData.getBTickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(normShort.get(index)));
            shortEntryPoint.setMarkerColor(Color.RED.darker());
            shortEntryPoint.setLineColor(Color.RED.darker());
            shortEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            shortEntryPoint.setLineStyle(new BasicStroke(0f));
        }

        if (entryData.getChartProfitMessage() != null && !entryData.getChartProfitMessage().isEmpty()) {
            Font font = new Font("Arial", Font.BOLD, 75);
            topChart.getStyler().setAnnotationTextFont(font);

            String[] lines = entryData.getChartProfitMessage().split("\n");
            double baseY = Collections.max(normLong) / 1.1;
            double x = timeAxis.get(timeAxis.size() / 2).getTime();

            for (int i = 0; i < lines.length; i++) {
                AnnotationText annotation = new AnnotationText(
                        lines[i],
                        x,
                        baseY - i * 0.6, // сдвиг по оси Y для каждой строки
                        false
                );
                topChart.addAnnotation(annotation);
            }
        }

        // Нижний график — спред (без заголовка и легенды сверху)
        XYChart bottomChart = new XYChartBuilder()
                .width(1920).height(360)
                .title("")  // убираем заголовок
                .xAxisTitle("")
                .yAxisTitle("Spread")
                .build();

        bottomChart.setTitle("Spread (long - β * short), β = " + String.format("%.4f", beta));

        bottomChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);  // легенда слева сверху
        bottomChart.getStyler().setLegendVisible(true);  // полностью скрыть легенду
        // Сделаем фон легенды полупрозрачным (например, белый с 50% прозрачности)
//        bottomChart.getStyler().setLegendBackgroundColor(new Color(255, 255, 255, 128)); // 128 из 255 = 50% прозрачности
        // Если хотите убрать рамку вокруг легенды или сделать ее тоже прозрачной
//        bottomChart.getStyler().setLegendBorderColor(new Color(0, 0, 0, 0)); // полностью прозрачная рамка
        bottomChart.getStyler().setXAxisTicksVisible(true);
        bottomChart.getStyler().setYAxisTicksVisible(false);
        bottomChart.getStyler().setYAxisTitleVisible(false);

        bottomChart.addSeries("Spread (" + bestPair.getSpread() + ")", timeAxis, spread).setMarker(SeriesMarkers.NONE);

        // Добавляем линии mean, mean ± std, mean ± 2*std
        List<Double> meanList = Collections.nCopies(spread.size(), mean);
        List<Double> std1Up = spread.stream().map(s -> mean + std).collect(Collectors.toList());
        List<Double> std1Down = spread.stream().map(s -> mean - std).collect(Collectors.toList());
        List<Double> std2Up = spread.stream().map(s -> mean + 2 * std).collect(Collectors.toList());
        List<Double> std2Down = spread.stream().map(s -> mean - 2 * std).collect(Collectors.toList());

        // Добавляем серию "Mean"
        XYSeries meanSeries = (XYSeries) bottomChart.addSeries("Mean", timeAxis, meanList);
        meanSeries.setLineColor(Color.BLACK);
        meanSeries.setMarker(new None());

        // Добавляем серию "+1σ"
        XYSeries plus1Sigma = (XYSeries) bottomChart.addSeries("+1σ", timeAxis, std1Up);
        plus1Sigma.setLineColor(Color.GRAY);
        plus1Sigma.setMarker(new None());
        plus1Sigma.setLineStyle(new BasicStroke(
                1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));

        // Добавляем серию "-1σ"
        XYSeries minus1Sigma = (XYSeries) bottomChart.addSeries("-1σ", timeAxis, std1Down);
        minus1Sigma.setLineColor(Color.GRAY);
        minus1Sigma.setMarker(new None());
        minus1Sigma.setLineStyle(new BasicStroke(
                1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));

        // Добавляем серию "+2σ"
        XYSeries plus2Sigma = (XYSeries) bottomChart.addSeries("+2σ", timeAxis, std2Up);
        plus2Sigma.setLineColor(Color.LIGHT_GRAY);
        plus2Sigma.setMarker(new None());
        plus2Sigma.setLineStyle(new BasicStroke(
                1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2f, 6f}, 0));

        // Добавляем серию "-2σ"
        XYSeries minus2Sigma = (XYSeries) bottomChart.addSeries("-2σ", timeAxis, std2Down);
        minus2Sigma.setLineColor(Color.LIGHT_GRAY);
        minus2Sigma.setMarker(new None());
        minus2Sigma.setLineStyle(new BasicStroke(
                1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2f, 6f}, 0));

        // Вертикальная линия по entryTime на графике спреда
        if (entryData.getEntryTime() > 0) {
            Date entryDate = new Date(entryData.getEntryTime());
            List<Date> lineX = Arrays.asList(entryDate, entryDate);
            // по Y рисуем от минимального до максимального значения спреда
            Double minSpread = Collections.min(spread);
            Double maxSpread = Collections.max(spread);
            List<Double> lineY = Arrays.asList(minSpread, maxSpread);

            XYSeries entryLineSpread = bottomChart.addSeries("Entry Point Spread", lineX, lineY);
            entryLineSpread.setLineColor(java.awt.Color.BLUE);
            entryLineSpread.setMarker(new None());
            entryLineSpread.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
        }

        //точка
        if (entryData.getEntryTime() > 0) {
            long entryTime = entryData.getEntryTime();

            // Найти ближайший индекс к entryTime
            int index = 0;
            for (int i = 0; i < timeAxis.size(); i++) {
                if (timeAxis.get(i).getTime() >= entryTime) {
                    index = i;
                    break;
                }
            }

            Date entryDate = timeAxis.get(index);

            // Добавляем точку входа на график спреда
            XYSeries spreadEntryPoint = bottomChart.addSeries("Spread Entry (" + entryData.getSpreadEntry() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(spread.get(index)));
            spreadEntryPoint.setMarkerColor(Color.BLUE.darker());
            spreadEntryPoint.setLineColor(Color.BLUE.darker());
            spreadEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            spreadEntryPoint.setLineStyle(new BasicStroke(0f)); // линия не рисуется
        }

        // Формируем подпись с параметрами
        double lastSpread = spread.get(spread.size() - 1);
        String caption = String.format("pvalue=%.4f, zscore=%.2f, mean=%.2f, spread=%.2f", bestPair.getPvalue(), bestPair.getZscore(), mean, lastSpread);

        // Генерируем BufferedImage из каждого графика
        BufferedImage topImg = BitmapEncoder.getBufferedImage(topChart);
        BufferedImage bottomImg = BitmapEncoder.getBufferedImage(bottomChart);

        // Объединяем в один график с подписью
        BufferedImage combined = ChartUtil.combineChartsWithoutGap(topImg, bottomImg, caption);

        // Сохраняем итоговый файл
        File dir = new File(CHARTS_DIR);
        if (!dir.exists()) dir.mkdirs();

        String filename = CHARTS_DIR + "/combined_chart_" + System.currentTimeMillis() + ".png";
        try {
            ImageIO.write(combined, "PNG", new File(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("Combined chart saved to {}", filename);
    }
}
