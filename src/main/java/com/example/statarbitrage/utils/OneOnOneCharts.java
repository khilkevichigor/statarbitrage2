package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ZScoreEntry;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
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

@Slf4j
public final class OneOnOneCharts {
    private static final String CHARTS_DIR = "charts";

    private OneOnOneCharts() {
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

        // Дата и цены
        List<Date> timeLong = longCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = longCandles.stream().map(Candle::getClose).toList();

        List<Date> timeShort = shortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).toList();

        // График 1: первая монета (long)
        XYChart topChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Price Chart: LONG (" + longTicker + ") - SHORT (" + shortTicker + ")")
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();
        topChart.getStyler().setLegendVisible(false);

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker + " (current " + entryData.getLongTickerCurrentPrice() + ")", timeLong, longPrices);
        longSeries.setLineColor(Color.GREEN);
        longSeries.setMarker(new None());

        topChart.getStyler().setYAxisTicksVisible(false);
        topChart.getStyler().setYAxisTitleVisible(false);

        topChart.getStyler().setXAxisTitleVisible(false);      // скрыть заголовок оси X

        // График 2: вторая монета (short)
        XYChart bottomChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Price Chart: LONG (" + longTicker + ") - SHORT (" + shortTicker + ")")
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();
        bottomChart.getStyler().setLegendVisible(false);

        XYSeries shortSeries = bottomChart.addSeries("SHORT: " + shortTicker + " (current " + entryData.getShortTickerCurrentPrice() + ")", timeShort, shortPrices);
        shortSeries.setLineColor(Color.RED);
        shortSeries.setMarker(new None());

        bottomChart.getStyler().setYAxisTicksVisible(false);
        bottomChart.getStyler().setYAxisTitleVisible(false);

        bottomChart.getStyler().setXAxisTitleVisible(false);      // скрыть заголовок оси X

        // Вертикальная линия по entryTime, если есть
        if (entryData.getEntryTime() > 0) {
            Date entryDate = new Date(entryData.getEntryTime());
            List<Date> lineX = Arrays.asList(entryDate, entryDate);

            double yMinTop = Collections.min(longPrices);
            double yMaxTop = Collections.max(longPrices);
            List<Double> lineYTop = Arrays.asList(yMinTop, yMaxTop);
            XYSeries entryLineTop = topChart.addSeries("Entry Point", lineX, lineYTop);
            entryLineTop.setLineColor(java.awt.Color.BLUE);
            entryLineTop.setMarker(new None());
            entryLineTop.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));

            double yMinBottom = Collections.min(shortPrices);
            double yMaxBottom = Collections.max(shortPrices);
            List<Double> lineYBottom = Arrays.asList(yMinBottom, yMaxBottom);
            XYSeries entryLineBottom = bottomChart.addSeries("Entry Point", lineX, lineYBottom);
            entryLineBottom.setLineColor(java.awt.Color.BLUE);
            entryLineBottom.setMarker(new None());
            entryLineBottom.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
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
            XYSeries longEntryPoint = topChart.addSeries("Long Entry (" + entryData.getLongTickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(longPrices.get(index)));
            longEntryPoint.setMarkerColor(Color.GREEN.darker());
            longEntryPoint.setLineColor(Color.GREEN.darker());
            longEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            longEntryPoint.setLineStyle(new BasicStroke(0f)); // линия не рисуется

            XYSeries shortEntryPoint = bottomChart.addSeries("Short Entry (" + entryData.getShortTickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(shortPrices.get(index)));
            shortEntryPoint.setMarkerColor(Color.RED.darker());
            shortEntryPoint.setLineColor(Color.RED.darker());
            shortEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            shortEntryPoint.setLineStyle(new BasicStroke(0f));
        }

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
