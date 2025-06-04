package com.example.statarbitrage.utils;

import com.example.statarbitrage.model.ZScorePoint;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public final class ComboProfitAndZChart {
    private static final String CHARTS_DIR = "charts";

    private ComboProfitAndZChart() {
    }

    public static void create(List<ZScorePoint> history) {
        if (history == null || history.isEmpty()) {
            log.warn("Empty history list");
            return;
        }

        List<Integer> xData = IntStream.range(0, history.size())
                .boxed()
                .collect(Collectors.toList());

        List<BigDecimal> profits = history.stream()
                .map(ZScorePoint::profit)
                .collect(Collectors.toList());

        List<BigDecimal> zScoreChanges = history.stream()
                .map(ZScorePoint::zScoreChanges)
                .collect(Collectors.toList());

        // --- Верхний график: Profit ---
        XYChart profitChart = new XYChartBuilder()
                .width(800).height(300)
                .title("Profit Over Time")
                .xAxisTitle("Point #")
                .yAxisTitle("Profit")
                .build();

        profitChart.getStyler().setLegendVisible(false);
        profitChart.getStyler().setMarkerSize(6);

        XYSeries profitSeries = profitChart.addSeries("Profit", xData, profits);
        profitSeries.setMarker(SeriesMarkers.CIRCLE);
        profitSeries.setLineStyle(new BasicStroke(0f));
        profitSeries.setMarkerColor(Color.GREEN);

        // --- Нижний график: Z-Score ---
        XYChart zScoreChart = new XYChartBuilder()
                .width(800).height(300)
                .title("Z-Score Over Time")
                .xAxisTitle("Point #")
                .yAxisTitle("Z-Score")
                .build();

        zScoreChart.getStyler().setLegendVisible(false);
        zScoreChart.getStyler().setMarkerSize(6);

        XYSeries zSeries = zScoreChart.addSeries("Z-Score", xData, zScoreChanges);
        zSeries.setMarker(SeriesMarkers.CIRCLE);
        zSeries.setLineStyle(new BasicStroke(0f));
        zSeries.setMarkerColor(Color.BLUE);

        // --- Сохраняем объединённый график ---
        try {
            BufferedImage profitImage = BitmapEncoder.getBufferedImage(profitChart);
            BufferedImage zImage = BitmapEncoder.getBufferedImage(zScoreChart);

            BufferedImage combinedImage = ChartUtil.combineChartsWithoutGap(profitImage, zImage, "Profit and Z-Score");

            File dir = new File(CHARTS_DIR);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "combined_chart_" + System.currentTimeMillis() + ".png");
            ImageIO.write(combinedImage, "png", file);
        } catch (IOException e) {
            log.error("Failed to generate combined chart", e);
        }
    }

}
