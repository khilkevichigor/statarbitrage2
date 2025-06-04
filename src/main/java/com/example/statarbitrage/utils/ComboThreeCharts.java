package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ZScoreEntry;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.None;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public final class ComboThreeCharts {
    private static final String CHARTS_DIR = "charts";

    private ComboThreeCharts() {
    }

    public static void create(ConcurrentHashMap<String, List<Candle>> candlesMap,
                              ZScoreEntry bestPair, EntryData entryData) {

        String longTicker = bestPair.getLongticker();
        String shortTicker = bestPair.getShortticker();

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
                .toList();

        List<Double> longPrices = longCandles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).collect(Collectors.toList());

        List<Double> normLong = ChartUtil.normalize(longPrices);
        List<Double> normShort = ChartUtil.normalize(shortPrices);

        List<Double> spread = new ArrayList<>();
        for (int i = 0; i < normLong.size(); i++) {
            spread.add(normLong.get(i) - normShort.get(i));
        }

        // Верхний график: нормализованные цены
        XYChart topChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Normalized Prices")
                .xAxisTitle("Time")
                .yAxisTitle("Normalized Price")
                .build();

        topChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        topChart.getStyler().setXAxisTicksVisible(true);
        topChart.getStyler().setYAxisTicksVisible(true);

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker, timeAxis, normLong);
        longSeries.setLineColor(Color.GREEN);
        longSeries.setMarker(new None());

        XYSeries shortSeries = topChart.addSeries("SHORT: " + shortTicker, timeAxis, normShort);
        shortSeries.setLineColor(Color.RED);
        shortSeries.setMarker(new None());

        // Средний график: спред
        XYChart bottomChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Spread (Long - Short)")
                .xAxisTitle("Time")
                .yAxisTitle("Spread")
                .build();

        bottomChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        bottomChart.getStyler().setXAxisTicksVisible(true);
        bottomChart.getStyler().setYAxisTicksVisible(true);

        XYSeries spreadSeries = bottomChart.addSeries("Spread", timeAxis, spread);
        spreadSeries.setLineColor(Color.BLUE);
        spreadSeries.setMarker(new None());

        // Нижний график: наложение обычных цен с прозрачностью
        XYChart overlayChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Overlay of Raw Prices")
                .xAxisTitle("Time")
                .yAxisTitle("Price")
                .build();

        overlayChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        overlayChart.getStyler().setXAxisTicksVisible(true);
        overlayChart.getStyler().setYAxisTicksVisible(true);

        Color transparentGreen = new Color(0, 255, 0, 128); // 50% прозрачный зелёный
        Color transparentRed = new Color(255, 0, 0, 128);   // 50% прозрачный красный

        XYSeries longOverlay = overlayChart.addSeries("LONG: " + longTicker, timeAxis, longPrices);
        longOverlay.setLineColor(transparentGreen);
        longOverlay.setMarker(new None());

        XYSeries shortOverlay = overlayChart.addSeries("SHORT: " + shortTicker, timeAxis, shortPrices);
        shortOverlay.setLineColor(transparentRed);
        shortOverlay.setMarker(new None());

        // Объединение всех трёх графиков в одно изображение
        BufferedImage topImage = BitmapEncoder.getBufferedImage(topChart);
        BufferedImage middleImage = BitmapEncoder.getBufferedImage(bottomChart);
        BufferedImage bottomImage = BitmapEncoder.getBufferedImage(overlayChart);

        BufferedImage combinedImage = new BufferedImage(1920, 2160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combinedImage.createGraphics();

        g2.drawImage(topImage, 0, 0, null);
        g2.drawImage(middleImage, 0, 720, null);
        g2.drawImage(bottomImage, 0, 1440, null);
        g2.dispose();

        try {
            String fileName = CHARTS_DIR + "/" + System.currentTimeMillis() + "_" + longTicker + "_" + shortTicker + ".png";
            File outputFile = new File(fileName);
            ImageIO.write(combinedImage, "png", outputFile);
            log.info("Сохранён комбинированный график: {}", outputFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Ошибка при сохранении графика", e);
        }

    }

    public static void createV2(ConcurrentHashMap<String, List<Candle>> candlesMap,
                                ZScoreEntry bestPair, EntryData entryData) {

        String longTicker = bestPair.getLongticker();
        String shortTicker = bestPair.getShortticker();

        List<Candle> longCandles = candlesMap.get(longTicker);
        List<Candle> shortCandles = candlesMap.get(shortTicker);

        if (longCandles == null || shortCandles == null) {
            log.warn("Не найдены свечи для тикеров {} или {}", longTicker, shortTicker);
            return;
        }

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Дата и цены
        List<Date> timeLong = longCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = longCandles.stream().map(Candle::getClose).toList();

        List<Date> timeShort = shortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).toList();

        // График 1: первая монета (long)
        XYChart topChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Price Chart: " + longTicker)
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();
        topChart.getStyler().setLegendVisible(false);

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker + " (current " + entryData.getLongTickerCurrentPrice() + ")", timeLong, longPrices);
        longSeries.setLineColor(java.awt.Color.GREEN);
        longSeries.setMarker(new None());


        // График 2: вторая монета (short)
        XYChart bottomChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Price Chart: " + shortTicker)
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();
        bottomChart.getStyler().setLegendVisible(false);

        XYSeries shortSeries = bottomChart.addSeries("SHORT: " + shortTicker + " (current " + entryData.getShortTickerCurrentPrice() + ")", timeLong, shortPrices);
        longSeries.setLineColor(Color.RED);
        shortSeries.setMarker(new None());

        // Объединение 2 графиков
        BufferedImage topImage = BitmapEncoder.getBufferedImage(topChart);
        BufferedImage bottomImage = BitmapEncoder.getBufferedImage(bottomChart);

        BufferedImage combinedImage = new BufferedImage(1920, 1440, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combinedImage.createGraphics();
        g2.drawImage(topImage, 0, 0, null);
        g2.drawImage(bottomImage, 0, 720, null);
        g2.dispose();

        try {
            String fileName = CHARTS_DIR + "/" + System.currentTimeMillis() + "_" + longTicker + "_" + shortTicker + ".png";
            File outputFile = new File(fileName);
            ImageIO.write(combinedImage, "png", outputFile);
            log.info("Сохранён график: {}", outputFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Ошибка при сохранении графика", e);
        }
    }

    public static void createV3(ConcurrentHashMap<String, List<Candle>> candlesMap,
                                ZScoreEntry bestPair, EntryData entryData) {

        String longTicker = bestPair.getLongticker();
        String shortTicker = bestPair.getShortticker();

        List<Candle> longCandles = candlesMap.get(longTicker);
        List<Candle> shortCandles = candlesMap.get(shortTicker);

        if (longCandles == null || shortCandles == null) {
            log.warn("Не найдены свечи для тикеров {} или {}", longTicker, shortTicker);
            return;
        }

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Дата и цены
        List<Date> timeLong = longCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = longCandles.stream().map(Candle::getClose).toList();

        List<Date> timeShort = shortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).toList();

        // График 1: первая монета (long)
        XYChart topChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Price Chart: " + longTicker)
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();
        topChart.getStyler().setLegendVisible(false);

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker + " (current " + entryData.getLongTickerCurrentPrice() + ")", timeLong, longPrices);
        longSeries.setLineColor(java.awt.Color.GREEN);
        longSeries.setMarker(new None());


        // График 2: вторая монета (short)
        XYChart bottomChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Price Chart: " + shortTicker)
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();
        bottomChart.getStyler().setLegendVisible(false);

        XYSeries shortSeries = bottomChart.addSeries("SHORT: " + shortTicker + " (current " + entryData.getShortTickerCurrentPrice() + ")", timeLong, shortPrices);
        longSeries.setLineColor(Color.RED);
        shortSeries.setMarker(new None());

        // Объединение 2 графиков
        BufferedImage topImage = BitmapEncoder.getBufferedImage(topChart);
        BufferedImage bottomImage = BitmapEncoder.getBufferedImage(bottomChart);

        BufferedImage combinedImage = new BufferedImage(1920, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combinedImage.createGraphics();

        // Нарисовать верхний график (long) полностью
        g2.drawImage(topImage, 0, 0, null);

        // Установить прозрачность 50% и наложить нижний график (short)
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2.drawImage(bottomImage, 0, 0, null);

        g2.dispose();

        try {
            String fileName = CHARTS_DIR + "/" + System.currentTimeMillis() + "_" + longTicker + "_" + shortTicker + ".png";
            File outputFile = new File(fileName);
            ImageIO.write(combinedImage, "png", outputFile);
            log.info("Сохранён график: {}", outputFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Ошибка при сохранении графика", e);
        }
    }

}
