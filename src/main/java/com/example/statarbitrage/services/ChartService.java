package com.example.statarbitrage.services;

import com.example.statarbitrage.events.SendAsPhotoEvent;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    public void generateJavaChartAndSend(String chatId, ConcurrentHashMap<String, List<Candle>> candlesMap, ZScoreEntry bestPair, EntryData entryData) {
        try {
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

            // Получение временных осей и цен
            List<Date> timeAxis = longCandles.stream()
                    .map(c -> Date.from(Instant.ofEpochMilli(c.getTimestamp())))
                    .collect(Collectors.toList());

            List<Double> rawLongPrices = longCandles.stream().map(Candle::getClose).collect(Collectors.toList());
            List<Double> rawShortPrices = shortCandles.stream().map(Candle::getClose).collect(Collectors.toList());

            List<Double> longPrices = normalizeZScore(rawLongPrices);
            List<Double> shortPrices = normalizeZScore(rawShortPrices);

            XYChart chart = new XYChartBuilder()
                    .width(900)
                    .height(600)
                    .title("Stat Arbitrage")
                    .xAxisTitle("Time")
                    .yAxisTitle("Price")
                    .build();

            XYSeries longSeries = chart.addSeries("LONG: " + longTicker + " (" + bestPair.getLongtickercurrentprice() + ")", timeAxis, longPrices);
            longSeries.setLineColor(java.awt.Color.GREEN);
            longSeries.setMarker(new None());

            XYSeries shortSeries = chart.addSeries("SHORT: " + shortTicker + " (" + bestPair.getShorttickercurrentprice() + ")", timeAxis, shortPrices);
            shortSeries.setLineColor(java.awt.Color.RED);
            shortSeries.setMarker(new None());

            chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideS);
            chart.getStyler().setLegendLayout(Styler.LegendLayout.Horizontal);
//            chart.getStyler().setPlotContentSize(.95);
//            chart.getStyler().setLegendPadding(5);
//            chart.getStyler().setChartPadding(5);
            chart.getStyler().setYAxisTicksVisible(false);  // скрыть деления (цифры)
            chart.getStyler().setYAxisTitleVisible(false);  // скрыть заголовок оси


            // Вертикальная линия по entryTime, если есть
            if (entryData.getEntryTime() > 0) {
                Date entryDate = new Date(entryData.getEntryTime());
                List<Date> lineX = Arrays.asList(entryDate, entryDate);
                List<Double> lineY = Arrays.asList(
                        Math.min(Collections.min(longPrices), Collections.min(shortPrices)),
                        Math.max(Collections.max(longPrices), Collections.max(shortPrices))
                );
                XYSeries entryLine = chart.addSeries("Entry Point", lineX, lineY);
                entryLine.setLineColor(java.awt.Color.BLUE);
                entryLine.setMarker(new None());
                entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
            }

            // Подпись профита, если есть
            if (entryData.getProfit() != null && !entryData.getProfit().isEmpty()) {
                chart.setTitle("Profit: " + entryData.getProfit());
            }

            // Создание директории, если не существует
            File dir = new File(CHARTS_DIR);
            if (!dir.exists()) dir.mkdirs();

            // Сохранение графика
            String filename = CHARTS_DIR + "/chart_" + System.currentTimeMillis() + ".png";
            BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
            log.info("Chart saved to {}", filename);

            sendChart(chatId, getChart(), "📊LONG " + bestPair.getLongticker() + ", SHORT " + bestPair.getShortticker(), true);
        } catch (IOException e) {
            log.error("Ошибка при сохранении чарта", e);
        }
    }

    public void generateCombinedChart(String chatId, ConcurrentHashMap<String, List<Candle>> candlesMap,
                                      ZScoreEntry bestPair) {

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
                .width(900).height(400)
                .title("Normalized Prices")
                .xAxisTitle("Time")
                .yAxisTitle("Normalized Price")
                .build();

        topChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        topChart.getStyler().setXAxisTicksVisible(false);  // чтобы не загромождать
        topChart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideS);
        topChart.getStyler().setLegendLayout(Styler.LegendLayout.Horizontal);
        topChart.getStyler().setYAxisTicksVisible(false);  // скрыть деления (цифры)
        topChart.getStyler().setYAxisTitleVisible(false);  // скрыть заголовок оси

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker, timeAxis, normLong);
        longSeries.setLineColor(Color.GREEN);
        longSeries.setMarker(new None());

        XYSeries shortSeries = topChart.addSeries("SHORT: " + shortTicker, timeAxis, normShort);
        shortSeries.setLineColor(Color.RED);
        shortSeries.setMarker(new None());

        // Создаём нижний график — спред и отклонения
        XYChart bottomChart = new XYChartBuilder()
                .width(900).height(200)
                .title("Spread with Mean and Std Deviations")
                .xAxisTitle("Time")
                .yAxisTitle("Spread")
                .build();

        bottomChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        bottomChart.getStyler().setXAxisTicksVisible(true);
        bottomChart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideS);
        bottomChart.getStyler().setLegendLayout(Styler.LegendLayout.Horizontal);
        bottomChart.getStyler().setYAxisTicksVisible(false);  // скрыть деления (цифры)
        bottomChart.getStyler().setYAxisTitleVisible(false);  // скрыть заголовок оси

        bottomChart.addSeries("Spread", timeAxis, spread).setMarker(SeriesMarkers.NONE);


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

    public void generateSpreadChart(String chatId,
                                    ConcurrentHashMap<String, List<Candle>> candlesMap,
                                    ZScoreEntry bestPair) {

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

        List<Date> timeAxis = longCandles.stream()
                .map(c -> new Date(c.getTimestamp()))
                .collect(Collectors.toList());

        List<Double> rawLongPrices = longCandles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Double> rawShortPrices = shortCandles.stream().map(Candle::getClose).collect(Collectors.toList());

        List<Double> longPrices = normalizeZScore(rawLongPrices);
        List<Double> shortPrices = normalizeZScore(rawShortPrices);

        // Расчёт спреда
        List<Double> spread = new ArrayList<>();
        for (int i = 0; i < longPrices.size(); i++) {
            spread.add(longPrices.get(i) - shortPrices.get(i));
        }

        double mean = spread.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double std = Math.sqrt(spread.stream().mapToDouble(s -> Math.pow(s - mean, 2)).average().orElse(0.0));
        double lastSpread = spread.get(spread.size() - 1);

        // Строим график спреда
        XYChart chart = new XYChartBuilder()
                .width(900)
                .height(500)
                .title("Spread")
                .xAxisTitle("Time")
                .yAxisTitle("Spread")
                .build();

        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setPlotMargin(0);
        chart.getStyler().setPlotContentSize(0.95);

        XYSeries spreadSeries = chart.addSeries("Spread", timeAxis, spread);
        spreadSeries.setLineColor(Color.BLUE);
        spreadSeries.setMarker(new None());


        List<Double> meanList = Collections.nCopies(spread.size(), mean);
        List<Double> std1Up = Collections.nCopies(spread.size(), mean + std);
        List<Double> std1Down = Collections.nCopies(spread.size(), mean - std);
        List<Double> std2Up = Collections.nCopies(spread.size(), mean + 2 * std);
        List<Double> std2Down = Collections.nCopies(spread.size(), mean - 2 * std);

        XYSeries meanSeries = chart.addSeries("Mean", timeAxis, meanList);
        meanSeries.setLineColor(Color.BLACK);
        meanSeries.setMarker(new None());

        XYSeries std1UpSeries = chart.addSeries("+1σ", timeAxis, std1Up);
        std1UpSeries.setLineColor(Color.GRAY);
        std1UpSeries.setLineStyle(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
        std1UpSeries.setMarker(new None());

        XYSeries std1DownSeries = chart.addSeries("-1σ", timeAxis, std1Down);
        std1DownSeries.setLineColor(Color.GRAY);
        std1DownSeries.setLineStyle(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0));
        std1DownSeries.setMarker(new None());

        XYSeries std2UpSeries = chart.addSeries("+2σ", timeAxis, std2Up);
        std2UpSeries.setLineColor(Color.LIGHT_GRAY);
        std2UpSeries.setLineStyle(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2f, 6f}, 0));
        std2UpSeries.setMarker(new None());

        XYSeries std2DownSeries = chart.addSeries("-2σ", timeAxis, std2Down);
        std2DownSeries.setLineColor(Color.LIGHT_GRAY);
        std2DownSeries.setLineStyle(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2f, 6f}, 0));
        std2DownSeries.setMarker(new None());

        // Добавляем подпись параметров
        BufferedImage chartImage = BitmapEncoder.getBufferedImage(chart);
        BufferedImage finalImage = new BufferedImage(chartImage.getWidth(), chartImage.getHeight() + 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, finalImage.getWidth(), finalImage.getHeight());
        g.drawImage(chartImage, 0, 0, null);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        String caption = String.format("pvalue=%.4f, zscore=%.2f, mean=%.2f, spread=%.2f", bestPair.getPvalue(), bestPair.getZscore(), mean, lastSpread);
        g.drawString(caption, 10, finalImage.getHeight() - 10);
        g.dispose();

        // Сохраняем картинку
        File dir = new File(CHARTS_DIR);
        if (!dir.exists()) dir.mkdirs();

        String filename = CHARTS_DIR + "/spread_chart_" + System.currentTimeMillis() + ".png";
        try {
            ImageIO.write(finalImage, "PNG", new File(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.info("Spread chart saved to {}", filename);

        sendChart(chatId, getChart(), "📈 Spread Chart", true);
    }

    private BufferedImage combineCharts(BufferedImage top, BufferedImage bottom, String caption) {
        int width = Math.max(top.getWidth(), bottom.getWidth());
        int height = top.getHeight() + bottom.getHeight() + 40; // + место для подписи

        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();

        // Белый фон
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // Нарисовать верхний график
        g.drawImage(top, 0, 0, null);

        // Нарисовать нижний график под верхним
        g.drawImage(bottom, 0, top.getHeight(), null);

        // Нарисовать подпись
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString(caption, 10, height - 10);

        g.dispose();
        return combined;
    }

    public BufferedImage combineChartsWithoutGap(BufferedImage topImg, BufferedImage bottomImg, String caption) {
        int width = Math.max(topImg.getWidth(), bottomImg.getWidth());
        int gap = 2;  // очень маленький gap между графиками
        int captionHeight = 30;  // высота области под подпись

        int height = topImg.getHeight() + gap + bottomImg.getHeight() + captionHeight;
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();

        // Рисуем верхний график (цены)
        g.drawImage(topImg, 0, 0, null);

        // Рисуем нижний график (спред) сразу под верхним с минимальным gap
        g.drawImage(bottomImg, 0, topImg.getHeight() + gap, null);

        // Рисуем подпись с параметрами ниже графиков
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        g.drawString(caption, 5, topImg.getHeight() + gap + bottomImg.getHeight() + 20);

        g.dispose();
        return combined;
    }



    public void generatePythonChartAndSend(String chatId, ZScoreEntry bestPair) {
        PythonScriptsExecuter.execute(PythonScripts.CREATE_CHART.getName(), true);
        sendChart(chatId, getChart(), "📊LONG " + bestPair.getLongticker() + ", SHORT " + bestPair.getShortticker(), true);
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
