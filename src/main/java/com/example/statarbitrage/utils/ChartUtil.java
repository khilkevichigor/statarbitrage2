package com.example.statarbitrage.utils;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public final class ChartUtil {
    private static final String CHARTS_DIR = "charts";

    private ChartUtil() {
    }

    public static BufferedImage combineChartsWithoutGap(BufferedImage topImg, BufferedImage bottomImg, String caption) {
        int width = Math.max(topImg.getWidth(), bottomImg.getWidth());
        int gap = 2;  // минимальный отступ между графиками
        int captionHeight = 30;

        int height = topImg.getHeight() + gap + bottomImg.getHeight() + captionHeight;

        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();

        g.drawImage(topImg, 0, 0, null);
        g.drawImage(bottomImg, 0, topImg.getHeight() + gap, null);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString(caption, 5, topImg.getHeight() + gap + bottomImg.getHeight() + 20);

        g.dispose();
        return combined;
    }

    public static List<Double> normalize(List<Double> series) {
        double mean = series.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double std = Math.sqrt(series.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0.0));

        if (std == 0) {
            return series.stream().map(v -> v - mean).collect(Collectors.toList());
        }

        return series.stream().map(v -> (v - mean) / std).collect(Collectors.toList());
    }
}
