package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.ZScoreEntry;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
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

@Slf4j
public final class OneOnOneCharts {
    private static final String CHARTS_DIR = "charts";

    private OneOnOneCharts() {
    }

    public static void create(ConcurrentHashMap<String, List<Candle>> candlesMap,
                              ZScoreEntry bestPair, PairData pairData) {

        String aTicker = bestPair.getA();
        String bTicker = bestPair.getB();

        List<Candle> aCandles = candlesMap.get(aTicker);
        List<Candle> bCandles = candlesMap.get(bTicker);

        if (aCandles == null || bCandles == null) {
            log.warn("Не найдены свечи для тикеров {} или {}", aTicker, bTicker);
            return;
        }

        // Сортировка по времени
        aCandles.sort(Comparator.comparing(Candle::getTimestamp));
        bCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Получение времени и цен
        List<Date> timeAxis = aCandles.stream()
                .map(c -> new Date(c.getTimestamp()))
                .toList();

        // Дата и цены
        List<Date> timeA = aCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> aPrices = aCandles.stream().map(Candle::getClose).toList();

        List<Date> timeB = bCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> bPrices = bCandles.stream().map(Candle::getClose).toList();

        // График 1: первая монета (long)
        XYChart topChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Price Chart: " + aTicker + " - " + bTicker)
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();
        topChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);  // легенда слева сверху
        topChart.getStyler().setLegendVisible(true);  // полностью скрыть легенду

        XYSeries aSeries = topChart.addSeries("A " + aTicker + " (current " + pairData.getATickerCurrentPrice() + ")", timeA, aPrices);
        aSeries.setLineColor(Color.MAGENTA);
        aSeries.setMarker(new None());

        topChart.getStyler().setYAxisTicksVisible(false);
        topChart.getStyler().setYAxisTitleVisible(false);

        topChart.getStyler().setXAxisTitleVisible(false);      // скрыть заголовок оси X

        // График 2: вторая монета (short)
        XYChart bottomChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Price Chart: " + aTicker + " - " + bTicker)
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();

        bottomChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);  // легенда слева сверху
        bottomChart.getStyler().setLegendVisible(true);  // полностью скрыть легенду

        XYSeries shortSeries = bottomChart.addSeries("B " + bTicker + " (current " + pairData.getBTickerCurrentPrice() + ")", timeB, bPrices);
        shortSeries.setLineColor(Color.BLUE);
        shortSeries.setMarker(new None());

        bottomChart.getStyler().setYAxisTicksVisible(false);
        bottomChart.getStyler().setYAxisTitleVisible(false);

        bottomChart.getStyler().setXAxisTitleVisible(false);      // скрыть заголовок оси X

        // Вертикальная линия по entryTime, если есть
        if (pairData.getEntryTime() > 0) {
            Date entryDate = new Date(pairData.getEntryTime());
            List<Date> lineX = Arrays.asList(entryDate, entryDate);

            double yMinTop = Collections.min(aPrices);
            double yMaxTop = Collections.max(aPrices);
            List<Double> lineYTop = Arrays.asList(yMinTop, yMaxTop);
            XYSeries entryLineTop = topChart.addSeries("Entry Point", lineX, lineYTop);
            entryLineTop.setLineColor(java.awt.Color.MAGENTA);
            entryLineTop.setMarker(new None());
            entryLineTop.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));

            double yMinBottom = Collections.min(bPrices);
            double yMaxBottom = Collections.max(bPrices);
            List<Double> lineYBottom = Arrays.asList(yMinBottom, yMaxBottom);
            XYSeries entryLineBottom = bottomChart.addSeries("Entry Point", lineX, lineYBottom);
            entryLineBottom.setLineColor(java.awt.Color.BLUE);
            entryLineBottom.setMarker(new None());
            entryLineBottom.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
        }

        //точки
        if (pairData.getEntryTime() > 0) {
            long entryTime = pairData.getEntryTime();

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
            XYSeries aEntryPoint = topChart.addSeries("Entry (" + pairData.getATickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(aPrices.get(index)));
            aEntryPoint.setMarkerColor(Color.MAGENTA.darker());
            aEntryPoint.setLineColor(Color.MAGENTA.darker());
            aEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            aEntryPoint.setLineStyle(new BasicStroke(0f)); // линия не рисуется

            XYSeries bEntryPoint = bottomChart.addSeries("Entry (" + pairData.getBTickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(bPrices.get(index)));
            bEntryPoint.setMarkerColor(Color.BLUE.darker());
            bEntryPoint.setLineColor(Color.BLUE.darker());
            bEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            bEntryPoint.setLineStyle(new BasicStroke(0f));
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
            String fileName = CHARTS_DIR + "/" + System.currentTimeMillis() + "_" + aTicker + "_" + bTicker + ".png";
            File outputFile = new File(fileName);
            ImageIO.write(combinedImage, "png", outputFile);
            log.info("Сохранён график: {}", outputFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Ошибка при сохранении графика", e);
        }
    }

    public static void createLogarithmic(ConcurrentHashMap<String, List<Candle>> candlesMap,
                                         ZScoreEntry bestPair, PairData pairData) {
        String aTicker = bestPair.getA();
        String bTicker = bestPair.getB();

        List<Candle> aCandles = candlesMap.get(aTicker);
        List<Candle> bCandles = candlesMap.get(bTicker);

        if (aCandles == null || bCandles == null) {
            log.warn("Не найдены свечи для тикеров {} или {}", aTicker, bTicker);
            return;
        }

        // Сортировка по времени
        aCandles.sort(Comparator.comparing(Candle::getTimestamp));
        bCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Получение времени и цен
        List<Date> timeAxis = aCandles.stream()
                .map(c -> new Date(c.getTimestamp()))
                .toList();

        // Дата и цены
        List<Date> timeA = aCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Date> timeB = bCandles.stream().map(c -> new Date(c.getTimestamp())).toList();

        List<Double> aPrices = aCandles.stream().map(Candle::getClose).map(Math::log).toList(); // логарифм цен
        List<Double> bPrices = bCandles.stream().map(Candle::getClose).map(Math::log).toList();

        // График 1: первая монета (long)
        XYChart topChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Log Price Chart: " + aTicker + " - " + bTicker)
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();
        topChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);  // легенда слева сверху
        topChart.getStyler().setLegendVisible(true);  // полностью скрыть легенду

        XYSeries aSeries = topChart.addSeries("Log A " + aTicker + " (current " + pairData.getATickerCurrentPrice() + ")", timeA, aPrices);
        aSeries.setLineColor(Color.MAGENTA);
        aSeries.setMarker(new None());

        topChart.getStyler().setYAxisTicksVisible(false);
        topChart.getStyler().setYAxisTitleVisible(false);

        topChart.getStyler().setXAxisTitleVisible(false);      // скрыть заголовок оси X

        // График 2: вторая монета (short)
        XYChart bottomChart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Log Price Chart: " + aTicker + " - " + bTicker)
                .xAxisTitle("Time").yAxisTitle("Price")
                .build();

        bottomChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);  // легенда слева сверху
        bottomChart.getStyler().setLegendVisible(true);  // полностью скрыть легенду

        XYSeries shortSeries = bottomChart.addSeries("Log B " + bTicker + " (current " + pairData.getBTickerCurrentPrice() + ")", timeB, bPrices);
        shortSeries.setLineColor(Color.BLUE);
        shortSeries.setMarker(new None());

        bottomChart.getStyler().setYAxisTicksVisible(false);
        bottomChart.getStyler().setYAxisTitleVisible(false);

        bottomChart.getStyler().setXAxisTitleVisible(false);      // скрыть заголовок оси X

        // Вертикальная линия по entryTime, если есть
        if (pairData.getEntryTime() > 0) {
            Date entryDate = new Date(pairData.getEntryTime());
            List<Date> lineX = Arrays.asList(entryDate, entryDate);

            double yMinTop = Collections.min(aPrices);
            double yMaxTop = Collections.max(aPrices);
            List<Double> lineYTop = Arrays.asList(yMinTop, yMaxTop);
            XYSeries entryLineTop = topChart.addSeries("Entry Point", lineX, lineYTop);
            entryLineTop.setLineColor(java.awt.Color.MAGENTA);
            entryLineTop.setMarker(new None());
            entryLineTop.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));

            double yMinBottom = Collections.min(bPrices);
            double yMaxBottom = Collections.max(bPrices);
            List<Double> lineYBottom = Arrays.asList(yMinBottom, yMaxBottom);
            XYSeries entryLineBottom = bottomChart.addSeries("Entry Point", lineX, lineYBottom);
            entryLineBottom.setLineColor(java.awt.Color.BLUE);
            entryLineBottom.setMarker(new None());
            entryLineBottom.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
        }

        //точки
        if (pairData.getEntryTime() > 0) {
            long entryTime = pairData.getEntryTime();

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
            XYSeries aEntryPoint = topChart.addSeries("Entry (" + pairData.getATickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(aPrices.get(index)));
            aEntryPoint.setMarkerColor(Color.MAGENTA.darker());
            aEntryPoint.setLineColor(Color.MAGENTA.darker());
            aEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            aEntryPoint.setLineStyle(new BasicStroke(0f)); // линия не рисуется

            XYSeries bEntryPoint = bottomChart.addSeries("Entry (" + pairData.getBTickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(bPrices.get(index)));
            bEntryPoint.setMarkerColor(Color.BLUE.darker());
            bEntryPoint.setLineColor(Color.BLUE.darker());
            bEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            bEntryPoint.setLineStyle(new BasicStroke(0f));
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
            String fileName = CHARTS_DIR + "/" + System.currentTimeMillis() + "_LOG_" + aTicker + "_" + bTicker + ".png";
            File outputFile = new File(fileName);
            ImageIO.write(combinedImage, "png", outputFile);
            log.info("Сохранён логарифмический график: {}", outputFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Ошибка при сохранении логарифмического графика", e);
        }
    }


}
