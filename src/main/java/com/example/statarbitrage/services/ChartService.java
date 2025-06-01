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
import org.springframework.stereotype.Service;

import java.awt.*;
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
