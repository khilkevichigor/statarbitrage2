package com.example.statarbitrage.processors;

import com.example.statarbitrage.api.OkxClient;
import com.example.statarbitrage.events.SendAsTextEvent;
import com.example.statarbitrage.model.*;
import com.example.statarbitrage.python.PythonScripts;
import com.example.statarbitrage.python.PythonScriptsExecuter;
import com.example.statarbitrage.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenerProcessor {

    private final OkxClient okxClient;
    private final EventSendService eventSendService;
    private final SettingsService settingsService;
    private final ProfitService profitService;
    private final EntryDataService entryDataService;
    private final ChartService chartService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;

    private final Map<String, AtomicBoolean> runningTrades = new ConcurrentHashMap<>();
    private final CointegrationService cointegrationService;

    @Async
    public void testTrade(String chatId) {
        AtomicBoolean isRunning = runningTrades.computeIfAbsent(chatId, k -> new AtomicBoolean(false));
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("testTrade уже выполняется для chatId = {}", chatId);
            return;
        }

        try {
            ZScoreEntry topPair = zScoreService.getTopPairEntry();
            EntryData entryData = entryDataService.getEntryData();
            Settings settings = settingsService.getSettings();

            ConcurrentHashMap<String, List<Candle>> candlesMap = okxClient.getCandlesMap(Set.of(entryData.getLongticker(), entryData.getShortticker()), settings);
            candlesService.save(candlesMap);
            entryDataService.updateCurrentPrices(entryData, candlesMap);
            entryDataService.setupEntryPointsIfNeededFromCandles(entryData, topPair, candlesMap);
            ProfitData profitData = profitService.calculateAndSetProfit(entryData, settings.getCapitalLong(), settings.getCapitalShort(), settings.getLeverage(), settings.getFeePctPerTrade());

            PythonScriptsExecuter.execute(PythonScripts.CREATE_Z_SCORE_FILE.getName(), true);
            chartService.clearChartDir();
            PythonScriptsExecuter.execute(PythonScripts.CREATE_CHART.getName(), true);

            chartService.sendChart(chatId, chartService.getChart(), profitData.getLogMessage(), false);
        } catch (Exception e) {
            log.error("❌ Ошибка в testTrade: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }
    }

    @Async
    public void sendBestChart(String chatId) {
        long startTime = System.currentTimeMillis();

        Settings settings = settingsService.getSettings();

        Set<String> swapTickers = okxClient.getSwapTickers();
        int totalSymbols = swapTickers.size();

        ConcurrentHashMap<String, List<Candle>> candlesMap = okxClient.getCandlesMap(swapTickers, settings);
        candlesService.filterByBlackList(candlesMap);

        List<ZScoreEntry> zScoreEntries = cointegrationService.analyzeCointegrationPairs(candlesMap);
        ZScoreEntry bestCointegratedPair = cointegrationService.findBestCointegratedPair(zScoreEntries);

        JFreeChart jFreeChart = buildOverlayChart(bestCointegratedPair, candlesMap);
        try {
            File chartsDir = new File("charts");
            if (!chartsDir.exists()) {
                chartsDir.mkdir();
            }
            ChartUtils.saveChartAsPNG(new File(chartsDir, "chart.png"), jFreeChart, 1000, 600);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        chartService.sendChart(chatId, chartService.getChart(), "чарт", true);

        long durationMillis = System.currentTimeMillis() - startTime;
        long minutes = durationMillis / 1000 / 60;
        long seconds = (durationMillis / 1000) % 60;
        log.info("Скан завершен. Обработано {} тикеров за {} мин {} сек", totalSymbols, minutes, seconds);
    }

    private void sendSignal(String chatId, String text) {
        System.out.println(text);
        sendText(chatId, text);
    }

    private void sendText(String chatId, String text) {
        eventSendService.sendAsText(SendAsTextEvent.builder()
                .chatId(chatId)
                .text(text)
                .enableMarkdown(true)
                .build());
    }

    public JFreeChart buildOverlayChart(ZScoreEntry entry, Map<String, List<Candle>> candlesMap) {
        String longTicker = entry.getLongticker();
        String shortTicker = entry.getShortticker();

        List<Candle> longCandles = candlesMap.get(longTicker);
        List<Candle> shortCandles = candlesMap.get(shortTicker);

        if (longCandles == null || shortCandles == null || longCandles.size() != shortCandles.size()) {
            throw new IllegalArgumentException("Некорректные данные для построения графика.");
        }

        List<Double> longPrices = longCandles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).collect(Collectors.toList());

        List<Double> normLong = normalize(longPrices);
        List<Double> normShort = normalize(shortPrices);

        TimeSeries seriesLong = new TimeSeries(longTicker + " (LONG)");
        TimeSeries seriesShort = new TimeSeries(shortTicker + " (SHORT)");

        for (int i = 0; i < normLong.size(); i++) {
            Date date = new Date(longCandles.get(i).getTimestamp());
            Double valLong = normLong.get(i);
            Double valShort = normShort.get(i);
            if (valLong != null && !valLong.isNaN()) {
                seriesLong.addOrUpdate(new Minute(date), valLong);
            }
            if (valShort != null && !valShort.isNaN()) {
                seriesShort.addOrUpdate(new Minute(date), valShort);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(seriesLong);
        dataset.addSeries(seriesShort);

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                "Нормализованные цены " + longTicker + " и " + shortTicker,
                "Время",
                "Цена (норм.)",
                dataset,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();

        // Белый фон
        chart.setBackgroundPaint(Color.WHITE);
        plot.setBackgroundPaint(Color.WHITE);

        // НЕ УБИРАЙ ось Y полностью, а сделай ее "невидимой"
        ValueAxis yAxis = plot.getRangeAxis();
        yAxis.setTickLabelsVisible(false);
        yAxis.setTickMarksVisible(false);
        yAxis.setAxisLineVisible(false);

        // Цвета линий
        plot.getRenderer().setSeriesPaint(0, Color.GREEN);
        plot.getRenderer().setSeriesPaint(1, Color.RED);

        // Текущие цены в подзаголовке
        double longCurrentPrice = longPrices.get(longPrices.size() - 1);
        double shortCurrentPrice = shortPrices.get(shortPrices.size() - 1);
        String subtitleText = String.format("Текущие цены: %s (LONG) = %.6f, %s (SHORT) = %.6f",
                longTicker, longCurrentPrice, shortTicker, shortCurrentPrice);
        chart.addSubtitle(new TextTitle(subtitleText, new Font("Arial", Font.PLAIN, 14)));

        return chart;
    }



    private List<Double> normalize(List<Double> prices) {
        double base = prices.get(0);
        return prices.stream().map(p -> p / base * 100).collect(Collectors.toList());
    }


}
