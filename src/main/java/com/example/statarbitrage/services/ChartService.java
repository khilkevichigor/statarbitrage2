package com.example.statarbitrage.services;

import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.model.ZScorePoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {
    private final EventSendService eventSendService;

    private static final String CHARTS_DIR = "charts";

    public void clearChartDir() {
        File dir = new File(CHARTS_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        clearChartDir();
                    }
                    if (!file.delete()) {
                        log.warn("Не удалось удалить файл: {}", file.getAbsolutePath());
                    }
                    log.info("Очистили папку с чартами");
                }
            }
        }
    }

    public File getChart() {
        File chartDir = new File(CHARTS_DIR);
        File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

        if (chartFiles != null && chartFiles.length > 0) {
            // Сортировка по времени последнего изменения (от новых к старым)
            Arrays.sort(chartFiles, Comparator.comparingLong(File::lastModified).reversed());
            return chartFiles[0]; // Самый свежий чарт
        }

        return null; // Если файлов нет
    }

    public void sendChart(String chatId, File chartFile, String caption, boolean withLogging) {
        try {
            eventSendService.sendAsPhoto(SendAsPhotoEvent.builder()
                    .chatId(chatId)
                    .photo(chartFile)
                    .caption(caption)
                    .build());
            if (withLogging) {
                log.info("📤 Чарт отправлен в Telegram: {}", chartFile.getName());
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при отправке чарта: {}", e.getMessage(), e);
        }
    }

    public void generateCombinedChart(String chatId, ConcurrentHashMap<String, List<Candle>> candlesMap,
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
                .collect(Collectors.toList());

        List<Double> longPrices = longCandles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).collect(Collectors.toList());

        // Нормализуем цены
        List<Double> normLong = normalizeZScore(longPrices);
        List<Double> normShort = normalizeZScore(shortPrices);

        // Расчёт спреда
        List<Double> spread = new ArrayList<>();
        for (int i = 0; i < longPrices.size(); i++) {
            spread.add(longPrices.get(i) - shortPrices.get(i));
        }
        double mean = spread.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double std = Math.sqrt(spread.stream().mapToDouble(s -> Math.pow(s - mean, 2)).average().orElse(0.0));

        // Создаём верхний график — нормализованные цены
        XYChart topChart = new XYChartBuilder()
                .width(1920).height(720)
                .title(getTitle(entryData))
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

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker + " (current " + entryData.getLongTickerCurrentPrice() + ")", timeAxis, normLong);
        longSeries.setLineColor(java.awt.Color.GREEN);
        longSeries.setMarker(new None());

        XYSeries shortSeries = topChart.addSeries("SHORT: " + shortTicker + " (current " + entryData.getShortTickerCurrentPrice() + ")", timeAxis, normShort);
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
            XYSeries longEntryPoint = topChart.addSeries("Long Entry (" + entryData.getLongTickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(normLong.get(index)));
            longEntryPoint.setMarkerColor(Color.GREEN.darker());
            longEntryPoint.setLineColor(Color.GREEN.darker());
            longEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            longEntryPoint.setLineStyle(new BasicStroke(0f)); // линия не рисуется

            XYSeries shortEntryPoint = topChart.addSeries("Short Entry (" + entryData.getShortTickerEntryPrice() + ")", Collections.singletonList(entryDate),
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
        BufferedImage combined = combineChartsWithoutGap(topImg, bottomImg, caption);

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

        sendChart(chatId, getChart(), "Stat Arbitrage Combined Chart", true);
    }

    public void generateCombinedChartOls(String chatId, ConcurrentHashMap<String, List<Candle>> candlesMap,
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
                .collect(Collectors.toList());

        List<Double> longPrices = longCandles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).collect(Collectors.toList());

        // Нормализуем цены
        List<Double> normLong = normalizeZScore(longPrices);
        List<Double> normShort = normalizeZScore(shortPrices);

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
                .title(getTitle(entryData))
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

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker + " (current " + entryData.getLongTickerCurrentPrice() + ")", timeAxis, normLong);
        longSeries.setLineColor(java.awt.Color.GREEN);
        longSeries.setMarker(new None());

        XYSeries shortSeries = topChart.addSeries("SHORT: " + shortTicker + " (current " + entryData.getShortTickerCurrentPrice() + ")", timeAxis, normShort);
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
            XYSeries longEntryPoint = topChart.addSeries("Long Entry (" + entryData.getLongTickerEntryPrice() + ")", Collections.singletonList(entryDate),
                    Collections.singletonList(normLong.get(index)));
            longEntryPoint.setMarkerColor(Color.GREEN.darker());
            longEntryPoint.setLineColor(Color.GREEN.darker());
            longEntryPoint.setMarker(SeriesMarkers.CIRCLE);
            longEntryPoint.setLineStyle(new BasicStroke(0f)); // линия не рисуется

            XYSeries shortEntryPoint = topChart.addSeries("Short Entry (" + entryData.getShortTickerEntryPrice() + ")", Collections.singletonList(entryDate),
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
        BufferedImage combined = combineChartsWithoutGap(topImg, bottomImg, caption);

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

        sendChart(chatId, getChart(), "Stat Arbitrage Combined Chart", true);
    }

    public void generateProfitVsZChart(String chatId, List<ZScorePoint> history) {
        if (history == null || history.isEmpty()) {
            log.warn("Empty ZScorePoint history");
            return;
        }

        List<Double> zScores = history.stream()
                .map(ZScorePoint::zScore)
                .collect(Collectors.toList());

        List<BigDecimal> profits = history.stream()
                .map(ZScorePoint::profit)
                .collect(Collectors.toList());

        if (zScores.size() != profits.size()) {
            log.warn("Z-Scores and profits size mismatch");
            return;
        }

        XYChart chart = new XYChartBuilder()
                .width(1200).height(600)
                .title("Profit vs Z-Score")
                .xAxisTitle("Z-Score")
                .yAxisTitle("Profit")
                .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(6);

        XYSeries profitSeries = chart.addSeries("Profit", zScores, profits);
        profitSeries.setMarker(SeriesMarkers.CIRCLE);
        profitSeries.setLineColor(Color.BLUE);

        double exitZ = 0.5;
        BigDecimal minProfit = Collections.min(profits);
        BigDecimal maxProfit = Collections.max(profits);

        List<Double> exitZLineX = Arrays.asList(exitZ, exitZ);
        List<BigDecimal> exitZLineY = Arrays.asList(minProfit, maxProfit);
        XYSeries exitLine = chart.addSeries("Exit Threshold (z=0.5)", exitZLineX, exitZLineY);
        exitLine.setLineColor(Color.RED);
        exitLine.setLineStyle(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 6f}, 0));
        exitLine.setMarker(SeriesMarkers.NONE);

        int exitIndex = 0;
        double minDiff = Double.MAX_VALUE;
        for (int i = 0; i < zScores.size(); i++) {
            double diff = Math.abs(zScores.get(i) - exitZ);
            if (diff < minDiff) {
                minDiff = diff;
                exitIndex = i;
            }
        }

        BigDecimal exitProfit = profits.get(exitIndex);
        double exitZValue = zScores.get(exitIndex);
        XYSeries exitPoint = chart.addSeries(
                String.format("Exit Point (z=%.2f, profit=%.2f)", exitZValue, exitProfit),
                Collections.singletonList(exitZValue),
                Collections.singletonList(exitProfit)
        );
        exitPoint.setMarkerColor(Color.RED.darker());
        exitPoint.setLineColor(Color.RED.darker());
        exitPoint.setMarker(SeriesMarkers.DIAMOND);
        exitPoint.setLineStyle(new BasicStroke(0f));

        try {
            BufferedImage img = BitmapEncoder.getBufferedImage(chart);
            File dir = new File(CHARTS_DIR);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "profit_vs_z_" + System.currentTimeMillis() + ".png");
            BitmapEncoder.saveBitmap(chart, file.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);

            sendChart(chatId, getChart(), "Stat Arbitrage Combined Chart", true);

        } catch (IOException e) {
            log.error("Ошибка генерации графика Profit vs Z", e);
        }
    }

    public void generateSimpleProfitVsZChart(String chatId, List<ZScorePoint> history) {
        if (history == null || history.isEmpty()) {
            log.warn("Empty or null history");
            return;
        }

        // Фильтруем только валидные значения
        List<Double> zScores = new ArrayList<>();
        List<BigDecimal> profits = new ArrayList<>();

        for (ZScorePoint point : history) {
            if (point != null) {
                zScores.add(point.zScore());
                profits.add(point.profit());
            }
        }

        if (zScores.isEmpty()) {
            log.warn("No valid ZScorePoints");
            return;
        }

        // Строим график
        XYChart chart = new XYChartBuilder()
                .width(800).height(400)
                .title("Z-Score vs Profit")
                .xAxisTitle("Z-Score")
                .yAxisTitle("Profit")
                .build();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setMarkerSize(6);

        XYSeries series = chart.addSeries("ZScore-Profit", zScores, profits);
        series.setMarker(SeriesMarkers.CIRCLE);
        series.setLineStyle(new BasicStroke(0f)); // убираем линии между точками
        series.setMarkerColor(Color.BLUE);

        // Сохраняем и отправляем
        try {
            File dir = new File(CHARTS_DIR);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "simple_chart_" + System.currentTimeMillis() + ".png");
            BitmapEncoder.saveBitmap(chart, file.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
            sendChart(chatId, getChart(), "Stat Arbitrage Combined Chart", true);
        } catch (IOException e) {
            log.error("Error saving simple chart", e);
        }
    }

    public void sendProfitChart(String chatId, List<ZScorePoint> history) {
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

        XYChart chart = new XYChartBuilder()
                .width(800).height(400)
                .title("Profit Over Time")
                .xAxisTitle("Point #")
                .yAxisTitle("Profit")
                .build();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setMarkerSize(6);

        XYSeries series = chart.addSeries("Profit", xData, profits);
        series.setMarker(SeriesMarkers.CIRCLE);
        series.setLineStyle(new BasicStroke(0f));
        series.setMarkerColor(Color.GREEN);

        // Сохраняем и отправляем
        try {
            File dir = new File(CHARTS_DIR);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "simple_profit_chart_" + System.currentTimeMillis() + ".png");
            BitmapEncoder.saveBitmap(chart, file.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
            sendChart(chatId, getChart(), "Stat Arbitrage Combined Chart", true);
        } catch (IOException e) {
            log.error("Error saving simple chart", e);
        }
    }

    public void sendZScoreChart(String chatId, List<ZScorePoint> history) {
        if (history == null || history.isEmpty()) {
            log.warn("Empty history list");
            return;
        }

        List<Integer> xData = IntStream.range(0, history.size())
                .boxed()
                .collect(Collectors.toList());

        List<Double> zScores = history.stream()
                .map(ZScorePoint::zScore)
                .collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(800).height(400)
                .title("Z-Score Over Time")
                .xAxisTitle("Point #")
                .yAxisTitle("Z-Score")
                .build();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setMarkerSize(6);

        XYSeries series = chart.addSeries("Z-Score", xData, zScores);
        series.setMarker(SeriesMarkers.CIRCLE);
        series.setLineStyle(new BasicStroke(0f));
        series.setMarkerColor(Color.BLUE);

        // Сохраняем и отправляем
        try {
            File dir = new File(CHARTS_DIR);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "simple_zscore_chart_" + System.currentTimeMillis() + ".png");
            BitmapEncoder.saveBitmap(chart, file.getAbsolutePath(), BitmapEncoder.BitmapFormat.PNG);
            sendChart(chatId, getChart(), "Stat Arbitrage Combined Chart", true);
        } catch (IOException e) {
            log.error("Error saving simple chart", e);
        }
    }


    private static String getTitle(EntryData entryData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cointegration: LONG ").append(entryData.getLongticker()).append(" - SHORT ").append(entryData.getShortticker());
        if (entryData.getProfitStr() != null && !entryData.getProfitStr().isEmpty()) {
            sb.append(" Profit: ").append(entryData.getProfitStr());
        }
        return sb.toString();
    }

    private BufferedImage combineChartsWithoutGap(BufferedImage topImg, BufferedImage bottomImg, String caption) {
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

    private List<Double> normalizeZScore(List<Double> series) {
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
