package com.example.statarbitrage.ui.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.PixelSpreadHistoryItem;
import com.example.statarbitrage.common.dto.ProfitHistoryItem;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.core.services.PixelSpreadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private final PixelSpreadService pixelSpreadService;

    public BufferedImage createZScoreChart(PairData pairData, boolean showEma, int emaPeriod, boolean showStochRsi, boolean showProfit, boolean showCombinedPrice, boolean showPixelSpread, boolean showEntryPoint) {
        log.debug("Создание расширенного Z-Score графика для пары: {} (EMA: {}, период: {}, StochRSI: {}, Profit: {}, CombinedPrice: {}, PixelSpread: {}, EntryPoint: {})",
                pairData.getPairName(), showEma, emaPeriod, showStochRsi, showProfit, showCombinedPrice, showPixelSpread, showEntryPoint);

        XYChart chart = buildEnhancedZScoreChart(pairData, showEma, emaPeriod, showStochRsi, showProfit, showCombinedPrice, showPixelSpread, showEntryPoint);

        return BitmapEncoder.getBufferedImage(chart);
    }

    private XYChart buildEnhancedZScoreChart(PairData pairData, boolean showEma, int emaPeriod, boolean showStochRsi, boolean showProfit, boolean showCombinedPrice, boolean showPixelSpread, boolean showEntryPoint) {
        XYChart chart = buildBasicZScoreChart(pairData, showEntryPoint);

        List<ZScoreParam> history = pairData.getZScoreHistory();

        if (history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста - невозможно рассчитать индикаторы");
            return chart;
        }

        List<Long> timestamps = history.stream()
                .map(ZScoreParam::getTimestamp)
                .toList();
        List<Double> zScores = history.stream()
                .map(ZScoreParam::getZscore)
                .collect(Collectors.toList());
        List<Date> timeAxis = timestamps.stream().map(Date::new).collect(Collectors.toList());

        if (showEma && zScores.size() >= emaPeriod) {
            addEmaToChart(chart, timeAxis, zScores, emaPeriod);
        }

        if (showStochRsi && zScores.size() >= 14) {
            addStochRsiToChart(chart, timeAxis, zScores);
        }

        if (showProfit) {
            addProfitToChart(chart, pairData);
        }

        if (showCombinedPrice) {
            addCombinedPricesToChart(chart, pairData);
        }

        if (showPixelSpread) {
            addPixelSpreadToZScoreChart(chart, pairData);
        }

        return chart;
    }

    private XYChart buildBasicZScoreChart(PairData pairData, boolean showEntryPoint) {
        List<ZScoreParam> history = pairData.getZScoreHistory();

        List<Long> timestamps;
        List<Double> zScores;

        if (history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста для пары {}, создаем минимальные данные", pairData.getPairName());
            timestamps = Collections.singletonList(System.currentTimeMillis());
            zScores = Collections.singletonList(pairData.getZScoreCurrent());
        } else {
            timestamps = history.stream()
                    .map(ZScoreParam::getTimestamp)
                    .collect(Collectors.toList());
            zScores = history.stream()
                    .map(ZScoreParam::getZscore)
                    .collect(Collectors.toList());

            log.debug("Используем реальную историю Z-Score: {} точек для пары {}", history.size(), pairData.getPairName());
        }

        log.debug("Временной диапазон графика от: {} - до: {}",
                new Date(timestamps.get(0)), new Date(timestamps.get(timestamps.size() - 1)));
        log.debug("Текущий Z-Score: {}", pairData.getZScoreCurrent());

        if (timestamps.size() != zScores.size()) {
            log.warn("⚠️ Неверные входные данные для построения Z-графика");
            throw new IllegalArgumentException("Timestamps and zScores lists must have the same size");
        }

        List<Date> timeAxis = timestamps.stream().map(Date::new).collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Z-Score LONG (" + pairData.getLongTicker() + ") - SHORT (" + pairData.getShortTicker() + ")")
                .xAxisTitle("Time").yAxisTitle("Z-Score")
                .build();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDatePattern("HH:mm");
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setYAxisTicksVisible(false);
        chart.getStyler().setYAxisTitleVisible(false);

        XYSeries zSeries = chart.addSeries("Z-Score", timeAxis, zScores);
        zSeries.setLineColor(Color.MAGENTA);
        zSeries.setMarker(new None());

        addHorizontalLine(chart, timeAxis, 3.0, Color.BLUE);
        addHorizontalLine(chart, timeAxis, 2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, 1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, 0.0, Color.BLACK);
        addHorizontalLine(chart, timeAxis, -1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, -2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, -3.0, Color.BLUE);

        // Отображаем точку входа только если включен соответствующий чекбокс
        if (showEntryPoint) {
            long entryTimestamp = pairData.getEntryTime() > 0 ? pairData.getEntryTime() : pairData.getTimestamp();
            long historyStart = timestamps.get(0);
            long historyEnd = timestamps.get(timestamps.size() - 1);

            log.debug("Проверка линии входа: entryTime={}, historyStart={}, historyEnd={}",
                    new Date(entryTimestamp), new Date(historyStart), new Date(historyEnd));
            log.debug("PairData: entryTime={}, timestamp={}", pairData.getEntryTime(), pairData.getTimestamp());

            boolean inRange = entryTimestamp > 0 && entryTimestamp >= historyStart && entryTimestamp <= historyEnd;

            if (inRange) {
                log.debug("Время входа попадает в диапазон истории - рисуем точную линию входа");

                OptionalInt indexOpt = findClosestIndex(timestamps, entryTimestamp);

                if (indexOpt.isPresent()) {
                    int index = indexOpt.getAsInt();

                    Date entryDate = new Date(entryTimestamp);
                    List<Date> lineX = Arrays.asList(entryDate, entryDate);

                    double minY = zScores.stream().min(Double::compareTo).orElse(-2.0);
                    double maxY = zScores.stream().max(Double::compareTo).orElse(2.0);
                    List<Double> lineY = Arrays.asList(minY, maxY);

                    XYSeries entryLine = chart.addSeries("Entry", lineX, lineY);
                    entryLine.setLineColor(Color.BLUE);
                    entryLine.setMarker(new None());
                    entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

                    double entryZScore = pairData.getZScoreEntry();
                    List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
                    List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

                    XYSeries entryHorizontalLine = chart.addSeries("Entry Z-Score", horizontalLineX, horizontalLineY);
                    entryHorizontalLine.setLineColor(Color.BLUE);
                    entryHorizontalLine.setMarker(new None());
                    entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

                    log.debug("✅ Линия входа добавлена на графике в позиции {}", index);
                }
            } else if (entryTimestamp > 0) {
                log.debug("⚠️ Время входа не попадает в диапазон истории - показываем приблизительную линию");

                Date entryDate;
                int index;

                if (entryTimestamp < historyStart) {
                    entryDate = new Date(historyStart);
                    index = 0;
                    log.debug("Показываем линию входа в начале графика");
                } else {
                    entryDate = new Date(historyEnd);
                    index = timestamps.size() - 1;
                    log.debug("Показываем линию входа в конце графика");
                }

                List<Date> lineX = Arrays.asList(entryDate, entryDate);
                double minY = zScores.stream().min(Double::compareTo).orElse(-2.0);
                double maxY = zScores.stream().max(Double::compareTo).orElse(2.0);
                List<Double> lineY = Arrays.asList(minY, maxY);

                XYSeries entryLine = chart.addSeries("Entry (approx)", lineX, lineY);
                entryLine.setLineColor(Color.ORANGE);
                entryLine.setMarker(new None());
                entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

                double entryZScore = pairData.getZScoreEntry();
                List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
                List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

                XYSeries entryHorizontalLine = chart.addSeries("Entry Z-Score (approx)", horizontalLineX, horizontalLineY);
                entryHorizontalLine.setLineColor(Color.ORANGE);
                entryHorizontalLine.setMarker(new None());
                entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

                log.debug("✅ Приблизительная линия входа добавлена на графике");
            } else {
                log.warn("⚠️ Время входа не задано (0) - линия входа не будет показана");
            }
        } else {
            log.debug("🎯 Отображение точки входа отключено через чекбокс");
        }

        return chart;
    }

    private void addHorizontalLine(XYChart chart, List<Date> timeAxis, double yValue, Color color) {
        List<Double> yLine = Arrays.asList(yValue, yValue);
        XYSeries line = chart.addSeries("level_" + yValue, Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1)), yLine);
        line.setLineColor(color);
        line.setMarker(new None());
        line.setLineStyle(new BasicStroke(2.5f));
    }

    private OptionalInt findClosestIndex(List<Long> timestamps, long targetTime) {
        for (int i = 0; i < timestamps.size(); i++) {
            if (timestamps.get(i) >= targetTime) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private void addEmaToChart(XYChart chart, List<Date> timeAxis, List<Double> zScores, int period) {
        List<Double> emaValues = calculateEMA(zScores, period);

        List<Date> emaTimeAxis = timeAxis.subList(period - 1, timeAxis.size());

        log.debug("Добавляем EMA({}) линию: {} точек", period, emaValues.size());

        XYSeries emaSeries = chart.addSeries("EMA(" + period + ")", emaTimeAxis, emaValues);
        emaSeries.setLineColor(Color.CYAN);
        emaSeries.setMarker(new None());
        emaSeries.setLineStyle(new BasicStroke(2.0f));
    }

    private List<Double> calculateEMA(List<Double> values, int period) {
        if (values.size() < period) {
            return new ArrayList<>();
        }

        List<Double> emaValues = new ArrayList<>();
        double multiplier = 2.0 / (period + 1);

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += values.get(i);
        }
        double firstEma = sum / period;
        emaValues.add(firstEma);

        for (int i = period; i < values.size(); i++) {
            double currentValue = values.get(i);
            double previousEma = emaValues.get(emaValues.size() - 1);
            double ema = (currentValue * multiplier) + (previousEma * (1 - multiplier));
            emaValues.add(ema);
        }

        return emaValues;
    }

    private void addStochRsiToChart(XYChart chart, List<Date> timeAxis, List<Double> zScores) {
        List<Double> stochRsiValues = calculateStochRSI(zScores, 14, 3, 3);

        if (stochRsiValues.isEmpty()) {
            log.warn("⚠️ Не удалось рассчитать StochRSI - недостаточно данных");
            return;
        }

        List<Date> stochRsiTimeAxis = timeAxis.subList(timeAxis.size() - stochRsiValues.size(), timeAxis.size());

        double minZScore = zScores.stream().min(Double::compareTo).orElse(-3.0);
        double maxZScore = zScores.stream().max(Double::compareTo).orElse(3.0);
        double range = maxZScore - minZScore;

        List<Double> scaledStochRsi = stochRsiValues.stream()
                .map(value -> minZScore + (value / 100.0) * range)
                .collect(Collectors.toList());

        log.debug("Добавляем StochRSI линию: {} точек (масштабированы в диапазон {}-{})",
                stochRsiValues.size(), minZScore, maxZScore);

        XYSeries stochRsiSeries = chart.addSeries("StochRSI", stochRsiTimeAxis, scaledStochRsi);
        stochRsiSeries.setLineColor(Color.ORANGE);
        stochRsiSeries.setMarker(new None());
        stochRsiSeries.setLineStyle(new BasicStroke(1.5f));

        double overboughtLevel = minZScore + (80.0 / 100.0) * range;
        double oversoldLevel = minZScore + (20.0 / 100.0) * range;

        addHorizontalLine(chart, timeAxis, overboughtLevel, Color.RED);
        addHorizontalLine(chart, timeAxis, oversoldLevel, Color.GREEN);
    }

    private List<Double> calculateStochRSI(List<Double> values, int rsiPeriod, int stochPeriod, int smoothK) {
        if (values.size() < rsiPeriod + stochPeriod + smoothK) {
            return new ArrayList<>();
        }

        List<Double> rsiValues = calculateRSI(values, rsiPeriod);

        if (rsiValues.size() < stochPeriod) {
            return new ArrayList<>();
        }

        List<Double> stochRsiRaw = new ArrayList<>();

        for (int i = stochPeriod - 1; i < rsiValues.size(); i++) {
            List<Double> rsiPeriodValues = rsiValues.subList(i - stochPeriod + 1, i + 1);
            double minRsi = rsiPeriodValues.stream().min(Double::compareTo).orElse(0.0);
            double maxRsi = rsiPeriodValues.stream().max(Double::compareTo).orElse(100.0);
            double currentRsi = rsiValues.get(i);

            double stochRsi;
            if (maxRsi - minRsi == 0) {
                stochRsi = 50.0;
            } else {
                stochRsi = ((currentRsi - minRsi) / (maxRsi - minRsi)) * 100.0;
            }

            stochRsiRaw.add(stochRsi);
        }

        if (stochRsiRaw.size() < smoothK) {
            return stochRsiRaw;
        }

        List<Double> smoothedStochRsi = new ArrayList<>();
        for (int i = smoothK - 1; i < stochRsiRaw.size(); i++) {
            double sum = 0;
            for (int j = i - smoothK + 1; j <= i; j++) {
                sum += stochRsiRaw.get(j);
            }
            smoothedStochRsi.add(sum / smoothK);
        }

        return smoothedStochRsi;
    }

    private List<Double> calculateRSI(List<Double> values, int period) {
        if (values.size() < period + 1) {
            return new ArrayList<>();
        }

        List<Double> rsiValues = new ArrayList<>();
        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        for (int i = 1; i < values.size(); i++) {
            double change = values.get(i) - values.get(i - 1);
            gains.add(Math.max(change, 0));
            losses.add(Math.max(-change, 0));
        }

        double avgGain = gains.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgLoss = losses.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));
        rsiValues.add(rsi);

        for (int i = period; i < gains.size(); i++) {
            avgGain = (avgGain * (period - 1) + gains.get(i)) / period;
            avgLoss = (avgLoss * (period - 1) + losses.get(i)) / period;

            rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            rsi = 100 - (100 / (1 + rs));
            rsiValues.add(rsi);
        }

        return rsiValues;
    }

    private void addProfitToChart(XYChart chart, PairData pairData) {
        List<ProfitHistoryItem> profitHistory = pairData.getProfitHistory();
        if (profitHistory == null || profitHistory.isEmpty()) {
            log.debug("📊 История профита пуста для пары {}, график профита не будет добавлен.", pairData.getPairName());
            return;
        }

        long entryTimestamp = pairData.getEntryTime() > 0 ? pairData.getEntryTime() : pairData.getTimestamp();

        List<ProfitHistoryItem> filteredProfitHistory = profitHistory.stream()
                .filter(item -> item.getTimestamp() >= entryTimestamp)
                .collect(Collectors.toList());

        if (filteredProfitHistory.isEmpty()) {
            log.debug("📊 Нет данных профита с момента входа для пары {}, используя все данные", pairData.getPairName());
            filteredProfitHistory = profitHistory;
        }

        if (filteredProfitHistory.isEmpty()) {
            log.debug("📊 История профита все еще пуста после всех проверок для пары {}", pairData.getPairName());
            return;
        }

        List<Date> profitTimeAxis = filteredProfitHistory.stream()
                .map(p -> new Date(p.getTimestamp()))
                .collect(Collectors.toList());
        List<Double> profitValues = filteredProfitHistory.stream()
                .map(ProfitHistoryItem::getProfitPercent)
                .collect(Collectors.toList());

        XYSeries profitSeries = chart.addSeries("Profit %", profitTimeAxis, profitValues);
        profitSeries.setYAxisGroup(1);
        profitSeries.setLineColor(Color.ORANGE);
        profitSeries.setMarker(new None());
        profitSeries.setLineStyle(new BasicStroke(2.0f));

        Date lastTime = profitTimeAxis.get(profitTimeAxis.size() - 1);
        Double lastValue = profitValues.get(profitValues.size() - 1);

        XYSeries lastPointSeries = chart.addSeries("Last Profit Point",
                Collections.singletonList(lastTime),
                Collections.singletonList(lastValue));
        lastPointSeries.setYAxisGroup(1);
        lastPointSeries.setMarker(SeriesMarkers.CIRCLE);
        lastPointSeries.setMarkerColor(Color.RED);

        chart.setYAxisGroupTitle(1, "Profit %");

        log.debug("✅ График профита успешно добавлен на чарт с точкой на последнем значении");
    }

    public BufferedImage createPriceChart(PairData pairData) {
        return createPriceChart(pairData, false);
    }

    public BufferedImage createPriceChartWithProfit(PairData pairData, boolean showPixelSpread, boolean showProfit, boolean showEntryPoint) {
        return createPriceChartInternal(pairData, showPixelSpread, showProfit, showEntryPoint);
    }

    public BufferedImage createPriceChart(PairData pairData, boolean showPixelSpread) {
        return createPriceChartInternal(pairData, showPixelSpread, false, false);
    }

    private BufferedImage createPriceChartInternal(PairData pairData, boolean showPixelSpread, boolean showProfit, boolean showEntryPoint) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();

        List<Candle> longCandles = pairData.getLongTickerCandles();
        List<Candle> shortCandles = pairData.getShortTickerCandles();
        List<ZScoreParam> history = pairData.getZScoreHistory();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("Не найдены свечи для тикеров {} или {}", longTicker, shortTicker);
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Синхронизация с Z-Score историей, если она доступна
        if (history != null && !history.isEmpty()) {
            long zScoreStartTime = history.get(0).getTimestamp();
            long zScoreEndTime = history.get(history.size() - 1).getTimestamp();
            long bufferTime = 300000; // 5 минут буфер

            log.debug("📊 Синхронизируем Price чарт с Z-Score диапазоном: {} - {}",
                    new Date(zScoreStartTime), new Date(zScoreEndTime));

            // Фильтруем свечи по временному диапазону Z-Score
            longCandles = longCandles.stream()
                    .filter(c -> c.getTimestamp() >= (zScoreStartTime - bufferTime) && c.getTimestamp() <= (zScoreEndTime + bufferTime))
                    .toList();

            shortCandles = shortCandles.stream()
                    .filter(c -> c.getTimestamp() >= (zScoreStartTime - bufferTime) && c.getTimestamp() <= (zScoreEndTime + bufferTime))
                    .toList();

            log.debug("📊 Отфильтрованные свечи для Price чарта: LONG {}, SHORT {}",
                    longCandles.size(), shortCandles.size());

            if (longCandles.isEmpty() || shortCandles.isEmpty()) {
                log.warn("⚠️ Нет свечей в Z-Score временном диапазоне для Price чарта");
                return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            }
        }

        // Дата и цены (используем отфильтрованные свечи)
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

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker + " (current " + pairData.getLongTickerCurrentPrice() + ")", timeLong, longPrices);
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

        XYSeries shortSeries = bottomChart.addSeries("SHORT: " + shortTicker + " (current " + pairData.getShortTickerCurrentPrice() + ")", timeShort, shortPrices);
        shortSeries.setLineColor(Color.RED);
        shortSeries.setMarker(new None());

        bottomChart.getStyler().setYAxisTicksVisible(false);
        bottomChart.getStyler().setYAxisTitleVisible(false);

        bottomChart.getStyler().setXAxisTitleVisible(false);      // скрыть заголовок оси X

        // Добавляем пиксельный спред если нужно
        if (showPixelSpread) {
            addPixelSpreadToPriceChart(topChart, pairData, timeLong, longPrices);
            addPixelSpreadToPriceChart(bottomChart, pairData, timeShort, shortPrices);
        }

        // Добавляем профит если нужно
        if (showProfit) {
            addProfitToChart(topChart, pairData);
            addProfitToChart(bottomChart, pairData);
        }

        // Добавляем точку входа если нужно
        if (showEntryPoint) {
            addEntryPointToPriceChart(topChart, pairData, timeLong, longPrices);
            addEntryPointToPriceChart(bottomChart, pairData, timeShort, shortPrices);
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

        return combinedImage;
    }

    private void addCombinedPricesToChart(XYChart chart, PairData pairData) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();

        List<Candle> longCandles = pairData.getLongTickerCandles();
        List<Candle> shortCandles = pairData.getShortTickerCandles();
        List<ZScoreParam> history = pairData.getZScoreHistory();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty() || history.isEmpty()) {
            log.warn("⚠️ Не найдены данные для наложения цен на Z-Score чарт: longCandles={}, shortCandles={}, history={}",
                    longCandles != null ? longCandles.size() : "null",
                    shortCandles != null ? shortCandles.size() : "null",
                    history.size());
            return;
        }

        // Получаем временной диапазон Z-Score истории как основной
        long zScoreStartTime = history.get(0).getTimestamp();
        long zScoreEndTime = history.get(history.size() - 1).getTimestamp();

        log.debug("📊 Z-Score временной диапазон: {} - {}", new Date(zScoreStartTime), new Date(zScoreEndTime));

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Фильтруем свечи по временному диапазону Z-Score с небольшим буфером
        long bufferTime = 300000; // 5 минут буфер
        List<Candle> filteredLongCandles = longCandles.stream()
                .filter(c -> c.getTimestamp() >= (zScoreStartTime - bufferTime) && c.getTimestamp() <= (zScoreEndTime + bufferTime))
                .toList();

        List<Candle> filteredShortCandles = shortCandles.stream()
                .filter(c -> c.getTimestamp() >= (zScoreStartTime - bufferTime) && c.getTimestamp() <= (zScoreEndTime + bufferTime))
                .toList();

        if (filteredLongCandles.isEmpty() || filteredShortCandles.isEmpty()) {
            log.warn("⚠️ Нет свечей в временном диапазоне Z-Score: LONG filtered={}, SHORT filtered={}",
                    filteredLongCandles.size(), filteredShortCandles.size());
            return;
        }

        log.debug("📊 Отфильтрованные свечи: LONG {} -> {}, SHORT {} -> {}",
                longCandles.size(), filteredLongCandles.size(),
                shortCandles.size(), filteredShortCandles.size());

        // Получение времени и цен для отфильтрованных свечей
        List<Date> timeLong = filteredLongCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = filteredLongCandles.stream().map(Candle::getClose).toList();

        List<Date> timeShort = filteredShortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = filteredShortCandles.stream().map(Candle::getClose).toList();

        // Найти диапазон Z-Score для масштабирования цен
        double minZScore = history.stream().mapToDouble(ZScoreParam::getZscore).min().orElse(-3.0);
        double maxZScore = history.stream().mapToDouble(ZScoreParam::getZscore).max().orElse(3.0);
        double zRange = maxZScore - minZScore;

        // Найти диапазон цен для нормализации (используем только отфильтрованные цены)
        double minLongPrice = longPrices.stream().min(Double::compareTo).orElse(0.0);
        double maxLongPrice = longPrices.stream().max(Double::compareTo).orElse(1.0);
        double longPriceRange = maxLongPrice - minLongPrice;

        double minShortPrice = shortPrices.stream().min(Double::compareTo).orElse(0.0);
        double maxShortPrice = shortPrices.stream().max(Double::compareTo).orElse(1.0);
        double shortPriceRange = maxShortPrice - minShortPrice;

        // Нормализация long цен в диапазон Z-Score
        List<Double> scaledLongPrices = longPrices.stream()
                .map(price -> longPriceRange != 0 ?
                        minZScore + ((price - minLongPrice) / longPriceRange) * zRange : minZScore)
                .toList();

        // Нормализация short цен в диапазон Z-Score
        List<Double> scaledShortPrices = shortPrices.stream()
                .map(price -> shortPriceRange != 0 ?
                        minZScore + ((price - minShortPrice) / shortPriceRange) * zRange : minZScore)
                .toList();

        log.debug("✅ Добавляем синхронизированные цены на Z-Score чарт: LONG {} точек (диапазон: {}-{}), SHORT {} точек (диапазон: {}-{})",
                scaledLongPrices.size(), minLongPrice, maxLongPrice,
                scaledShortPrices.size(), minShortPrice, maxShortPrice);

        // Добавляем long цены как полупрозрачную зеленую линию
        XYSeries longPriceSeries = chart.addSeries("LONG Price (scaled): " + longTicker, timeLong, scaledLongPrices);
        longPriceSeries.setLineColor(new Color(0, 255, 0, 120)); // Полупрозрачный зеленый
        longPriceSeries.setMarker(new None());
        longPriceSeries.setLineStyle(new BasicStroke(1.5f));

        // Добавляем short цены как полупрозрачную красную линию
        XYSeries shortPriceSeries = chart.addSeries("SHORT Price (scaled): " + shortTicker, timeShort, scaledShortPrices);
        shortPriceSeries.setLineColor(new Color(255, 0, 0, 120)); // Полупрозрачный красный
        shortPriceSeries.setMarker(new None());
        shortPriceSeries.setLineStyle(new BasicStroke(1.5f));

        // Используем PixelSpreadService для расчёта пиксельного спреда
        pixelSpreadService.calculatePixelSpreadIfNeeded(pairData);
    }

    /**
     * Добавляет пиксельный спред на Z-Score чарт
     */
    private void addPixelSpreadToZScoreChart(XYChart chart, PairData pairData) {
        List<PixelSpreadHistoryItem> pixelHistory = pairData.getPixelSpreadHistory();

        if (pixelHistory == null || pixelHistory.isEmpty()) {
            log.warn("📊 История пиксельного спреда пуста для пары {}, не можем добавить на Z-Score чарт", pairData.getPairName());
            return;
        }

        // Сортируем по времени
        pixelHistory.sort(Comparator.comparing(PixelSpreadHistoryItem::getTimestamp));

        List<Date> timeAxis = pixelHistory.stream()
                .map(item -> new Date(item.getTimestamp()))
                .collect(Collectors.toList());
        List<Double> pixelDistances = pixelHistory.stream()
                .map(PixelSpreadHistoryItem::getPixelDistance)
                .collect(Collectors.toList());

        // Найти диапазон Z-Score для масштабирования пиксельного спреда
        List<ZScoreParam> history = pairData.getZScoreHistory();
        double minZScore = history.stream().mapToDouble(ZScoreParam::getZscore).min().orElse(-3.0);
        double maxZScore = history.stream().mapToDouble(ZScoreParam::getZscore).max().orElse(3.0);
        double zRange = maxZScore - minZScore;

        // Найти диапазон пиксельного спреда
        double minPixelDistance = pixelDistances.stream().min(Double::compareTo).orElse(0.0);
        double maxPixelDistance = pixelDistances.stream().max(Double::compareTo).orElse(100.0);
        double pixelRange = maxPixelDistance - minPixelDistance;

        // Масштабируем пиксельный спред в диапазон Z-Score
        List<Double> scaledPixelSpread = pixelDistances.stream()
                .map(pixel -> pixelRange != 0 ?
                        minZScore + ((pixel - minPixelDistance) / pixelRange) * zRange : minZScore)
                .collect(Collectors.toList());

        log.debug("✅ Добавляем пиксельный спред на Z-Score чарт: {} точек (диапазон: {}-{})",
                scaledPixelSpread.size(), minPixelDistance, maxPixelDistance);

        // Добавляем пиксельный спред как полупрозрачную фиолетовую линию
        XYSeries pixelSpreadSeries = chart.addSeries("Pixel Spread (scaled)", timeAxis, scaledPixelSpread);
        pixelSpreadSeries.setLineColor(new Color(128, 0, 128, 150)); // Полупрозрачный фиолетовый
        pixelSpreadSeries.setMarker(new None());
        pixelSpreadSeries.setLineStyle(new BasicStroke(2.0f));
    }

    /**
     * Добавляет пиксельный спред на Price чарт
     */
    private void addPixelSpreadToPriceChart(XYChart chart, PairData pairData, List<Date> priceTimeAxis, List<Double> prices) {
        List<PixelSpreadHistoryItem> pixelHistory = pairData.getPixelSpreadHistory();

        if (pixelHistory == null || pixelHistory.isEmpty()) {
            log.warn("📊 История пиксельного спреда пуста для пары {}, не можем добавить на Price чарт", pairData.getPairName());
            return;
        }

        // Сортируем по времени
        pixelHistory.sort(Comparator.comparing(PixelSpreadHistoryItem::getTimestamp));

        List<Date> timeAxis = pixelHistory.stream()
                .map(item -> new Date(item.getTimestamp()))
                .collect(Collectors.toList());
        List<Double> pixelDistances = pixelHistory.stream()
                .map(PixelSpreadHistoryItem::getPixelDistance)
                .collect(Collectors.toList());

        // Найти диапазон цен для масштабирования пиксельного спреда
        double minPrice = prices.stream().min(Double::compareTo).orElse(0.0);
        double maxPrice = prices.stream().max(Double::compareTo).orElse(1.0);
        double priceRange = maxPrice - minPrice;

        // Найти диапазон пиксельного спреда
        double minPixelDistance = pixelDistances.stream().min(Double::compareTo).orElse(0.0);
        double maxPixelDistance = pixelDistances.stream().max(Double::compareTo).orElse(100.0);
        double pixelRange = maxPixelDistance - minPixelDistance;

        // Масштабируем пиксельный спред в диапазон цен
        List<Double> scaledPixelSpread = pixelDistances.stream()
                .map(pixel -> pixelRange != 0 ?
                        minPrice + ((pixel - minPixelDistance) / pixelRange) * priceRange : minPrice)
                .collect(Collectors.toList());

        log.debug("✅ Добавляем пиксельный спред на Price чарт: {} точек (диапазон цен: {}-{}, диапазон пикселей: {}-{})",
                scaledPixelSpread.size(), minPrice, maxPrice, minPixelDistance, maxPixelDistance);

        // Добавляем пиксельный спред как полупрозрачную синюю линию
        XYSeries pixelSpreadSeries = chart.addSeries("Pixel Spread (scaled)", timeAxis, scaledPixelSpread);
        pixelSpreadSeries.setLineColor(new Color(0, 0, 255, 120)); // Полупрозрачный синий
        pixelSpreadSeries.setMarker(new None());
        pixelSpreadSeries.setLineStyle(new BasicStroke(2.0f));
    }

    /**
     * Добавляет точку входа на Price чарт
     */
    private void addEntryPointToPriceChart(XYChart chart, PairData pairData, List<Date> timeAxis, List<Double> prices) {
        long entryTimestamp = pairData.getEntryTime() > 0 ? pairData.getEntryTime() : pairData.getTimestamp();

        if (entryTimestamp <= 0 || timeAxis.isEmpty() || prices.isEmpty()) {
            log.debug("⚠️ Недостаточно данных для отображения точки входа на Price чарт");
            return;
        }

        long historyStart = timeAxis.get(0).getTime();
        long historyEnd = timeAxis.get(timeAxis.size() - 1).getTime();

        Date entryDate;
        boolean inRange = entryTimestamp >= historyStart && entryTimestamp <= historyEnd;

        if (inRange) {
            entryDate = new Date(entryTimestamp);
            log.debug("🎯 Время входа попадает в диапазон Price чарта - рисуем точную линию входа");
        } else if (entryTimestamp < historyStart) {
            entryDate = new Date(historyStart);
            log.debug("🎯 Время входа до диапазона Price чарта - показываем линию в начале");
        } else {
            entryDate = new Date(historyEnd);
            log.debug("🎯 Время входа после диапазона Price чарта - показываем линию в конце");
        }

        // Вертикальная линия входа
        double minPrice = prices.stream().min(Double::compareTo).orElse(0.0);
        double maxPrice = prices.stream().max(Double::compareTo).orElse(1.0);

        List<Date> verticalLineX = Arrays.asList(entryDate, entryDate);
        List<Double> verticalLineY = Arrays.asList(minPrice, maxPrice);

        Color lineColor = inRange ? Color.BLUE : Color.ORANGE;
        String seriesName = inRange ? "Entry Point" : "Entry Point (approx)";

        XYSeries entryVerticalLine = chart.addSeries(seriesName, verticalLineX, verticalLineY);
        entryVerticalLine.setLineColor(lineColor);
        entryVerticalLine.setMarker(new None());
        entryVerticalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

        log.debug("✅ Точка входа добавлена на Price чарт");
    }

    /**
     * Вычисляет пиксельное расстояние между графиками Long и Short цен и сохраняет в историю
     */
    private void calculateAndSavePixelSpread(PairData pairData, List<Date> timeLong, List<Double> scaledLongPrices,
                                             List<Date> timeShort, List<Double> scaledShortPrices) {
        log.debug("🔢 Начинаем вычисление пиксельного спреда для пары {}", pairData.getPairName());

        if (timeLong.isEmpty() || timeShort.isEmpty() || scaledLongPrices.isEmpty() || scaledShortPrices.isEmpty()) {
            log.warn("⚠️ Недостаточно данных для вычисления пиксельного спреда");
            return;
        }

        int chartHeight = 720; // Высота чарта из buildBasicZScoreChart

        // Находим диапазон масштабированных значений
        double minValue = Math.min(
                scaledLongPrices.stream().min(Double::compareTo).orElse(0.0),
                scaledShortPrices.stream().min(Double::compareTo).orElse(0.0)
        );
        double maxValue = Math.max(
                scaledLongPrices.stream().max(Double::compareTo).orElse(1.0),
                scaledShortPrices.stream().max(Double::compareTo).orElse(1.0)
        );
        double valueRange = maxValue - minValue;

        // Создаем синхронизированные временные точки
        Set<Long> allTimestamps = new HashSet<>();
        timeLong.forEach(date -> allTimestamps.add(date.getTime()));
        timeShort.forEach(date -> allTimestamps.add(date.getTime()));

        List<Long> sortedTimestamps = allTimestamps.stream().sorted().toList();

        log.debug("🔢 Найдено {} уникальных временных точек для анализа пиксельного спреда", sortedTimestamps.size());

        for (Long timestamp : sortedTimestamps) {
            // Находим ближайшие значения для Long и Short в данный момент времени
            Double longPrice = findNearestPrice(timeLong, scaledLongPrices, timestamp);
            Double shortPrice = findNearestPrice(timeShort, scaledShortPrices, timestamp);

            if (longPrice != null && shortPrice != null) {
                // Конвертируем значения в пиксели относительно высоты чарта
                double longPixelY = convertValueToPixel(longPrice, minValue, maxValue, chartHeight);
                double shortPixelY = convertValueToPixel(shortPrice, minValue, maxValue, chartHeight);

                // Вычисляем абсолютное пиксельное расстояние
                double pixelDistance = Math.abs(longPixelY - shortPixelY);

                // Сохраняем в историю пиксельного спреда
                PixelSpreadHistoryItem pixelSpreadItem = new PixelSpreadHistoryItem(timestamp, pixelDistance);
                pairData.addPixelSpreadPoint(pixelSpreadItem);

                log.trace("🔢 Timestamp: {}, Long: {} px, Short: {} px, Distance: {} px",
                        new Date(timestamp), Math.round(longPixelY), Math.round(shortPixelY), Math.round(pixelDistance));
            }
        }

        log.debug("✅ Пиксельный спред вычислен и сохранен. Всего точек: {}",
                pairData.getPixelSpreadHistory().size());
    }

    /**
     * Находит ближайшую цену для заданного времени
     */
    private Double findNearestPrice(List<Date> timeAxis, List<Double> prices, long targetTimestamp) {
        if (timeAxis.isEmpty() || prices.isEmpty()) return null;

        int bestIndex = 0;
        long bestDiff = Math.abs(timeAxis.get(0).getTime() - targetTimestamp);

        for (int i = 1; i < timeAxis.size(); i++) {
            long diff = Math.abs(timeAxis.get(i).getTime() - targetTimestamp);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIndex = i;
            }
        }

        return prices.get(bestIndex);
    }

    /**
     * Конвертирует значение в пиксельную координату Y (перевернутая система координат)
     */
    private double convertValueToPixel(double value, double minValue, double maxValue, int chartHeight) {
        if (maxValue - minValue == 0) return chartHeight / 2.0;

        // Нормализуем значение в диапазон [0, 1]
        double normalized = (value - minValue) / (maxValue - minValue);

        // Конвертируем в пиксели (Y=0 вверху, Y=chartHeight внизу)
        return chartHeight - (normalized * chartHeight);
    }

    /**
     * Вычисляет пиксельный спред независимо от отображения объединенных цен
     */
    public void calculatePixelSpreadIfNeeded(PairData pairData) {
        if (pairData.getPixelSpreadHistory().isEmpty()) {
            log.debug("🔢 Пиксельный спред не вычислен, вычисляем независимо от чекбокса объединенных цен");
            calculatePixelSpreadForPair(pairData);
        }
    }

    /**
     * Вычисляет пиксельный спред для пары
     */
    private void calculatePixelSpreadForPair(PairData pairData) {
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();

        List<Candle> longCandles = pairData.getLongTickerCandles();
        List<Candle> shortCandles = pairData.getShortTickerCandles();
        List<ZScoreParam> history = pairData.getZScoreHistory();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty() || history.isEmpty()) {
            log.warn("⚠️ Не найдены данные для вычисления пиксельного спреда: longCandles={}, shortCandles={}, history={}",
                    longCandles != null ? longCandles.size() : "null",
                    shortCandles != null ? shortCandles.size() : "null",
                    history.size());
            return;
        }

        // Получаем временной диапазон Z-Score истории как основной
        long zScoreStartTime = history.get(0).getTimestamp();
        long zScoreEndTime = history.get(history.size() - 1).getTimestamp();

        log.debug("📊 Z-Score временной диапазон: {} - {}", new Date(zScoreStartTime), new Date(zScoreEndTime));

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Фильтруем свечи по временному диапазону Z-Score с небольшим буфером
        long bufferTime = 300000; // 5 минут буфер
        List<Candle> filteredLongCandles = longCandles.stream()
                .filter(c -> c.getTimestamp() >= (zScoreStartTime - bufferTime) && c.getTimestamp() <= (zScoreEndTime + bufferTime))
                .toList();

        List<Candle> filteredShortCandles = shortCandles.stream()
                .filter(c -> c.getTimestamp() >= (zScoreStartTime - bufferTime) && c.getTimestamp() <= (zScoreEndTime + bufferTime))
                .toList();

        if (filteredLongCandles.isEmpty() || filteredShortCandles.isEmpty()) {
            log.warn("⚠️ Нет свечей в временном диапазоне Z-Score: LONG filtered={}, SHORT filtered={}",
                    filteredLongCandles.size(), filteredShortCandles.size());
            return;
        }

        // Получение времени и цен для отфильтрованных свечей
        List<Date> timeLong = filteredLongCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = filteredLongCandles.stream().map(Candle::getClose).toList();

        List<Date> timeShort = filteredShortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = filteredShortCandles.stream().map(Candle::getClose).toList();

        // Найти диапазон Z-Score для масштабирования цен
        double minZScore = history.stream().mapToDouble(ZScoreParam::getZscore).min().orElse(-3.0);
        double maxZScore = history.stream().mapToDouble(ZScoreParam::getZscore).max().orElse(3.0);
        double zRange = maxZScore - minZScore;

        // Найти диапазон цен для нормализации (используем только отфильтрованные цены)
        double minLongPrice = longPrices.stream().min(Double::compareTo).orElse(0.0);
        double maxLongPrice = longPrices.stream().max(Double::compareTo).orElse(1.0);
        double longPriceRange = maxLongPrice - minLongPrice;

        double minShortPrice = shortPrices.stream().min(Double::compareTo).orElse(0.0);
        double maxShortPrice = shortPrices.stream().max(Double::compareTo).orElse(1.0);
        double shortPriceRange = maxShortPrice - minShortPrice;

        // Нормализация long цен в диапазон Z-Score
        List<Double> scaledLongPrices = longPrices.stream()
                .map(price -> longPriceRange != 0 ?
                        minZScore + ((price - minLongPrice) / longPriceRange) * zRange : minZScore)
                .toList();

        // Нормализация short цен в диапазон Z-Score
        List<Double> scaledShortPrices = shortPrices.stream()
                .map(price -> shortPriceRange != 0 ?
                        minZScore + ((price - minShortPrice) / shortPriceRange) * zRange : minZScore)
                .toList();

        log.debug("✅ Вычисляем пиксельный спред независимо: LONG {} точек (диапазон: {}-{}), SHORT {} точек (диапазон: {}-{})",
                scaledLongPrices.size(), minLongPrice, maxLongPrice,
                scaledShortPrices.size(), minShortPrice, maxShortPrice);

        // Вычисляем пиксельное расстояние между графиками long и short
        calculateAndSavePixelSpread(pairData, timeLong, scaledLongPrices, timeShort, scaledShortPrices);
    }

    /**
     * Создает комбинированный чарт с выбранными компонентами
     */
    public BufferedImage createCombinedChart(PairData pairData, boolean showZScore, boolean showCombinedPrice,
                                             boolean showPixelSpread, boolean showEma, int emaPeriod,
                                             boolean showStochRsi, boolean showProfit, boolean showEntryPoint) {
        log.debug("🎨 Создание комбинированного чарта для пары: {} (ZScore: {}, Price: {}, PixelSpread: {}, EMA: {}, StochRSI: {}, Profit: {})",
                pairData.getPairName(), showZScore, showCombinedPrice, showPixelSpread, showEma, showStochRsi, showProfit);

        // Если выбран только один тип чарта, используем специализированные методы
        if (showZScore && !showCombinedPrice && !showPixelSpread) {
            return createZScoreChart(pairData, showEma, emaPeriod, showStochRsi, showProfit, false, false, showEntryPoint);
        } else if (showCombinedPrice && !showZScore && !showPixelSpread) {
            return createPriceChartInternal(pairData, false, false, showEntryPoint);
        } else if (showPixelSpread && !showZScore && !showCombinedPrice) {
            return createPixelSpreadChartInternal(pairData, false, showEntryPoint);
        }

        // Для комбинированного чарта используем Z-Score как базу
        XYChart chart;

        if (showZScore) {
            // Если Z-Score выбран, используем его как основу
            chart = buildEnhancedZScoreChart(pairData, showEma, emaPeriod, showStochRsi, showProfit, showCombinedPrice, showPixelSpread, showEntryPoint);
        } else {
            // Если Z-Score не выбран, создаем базовый чарт для других компонентов
            chart = createBaseCombinedChart(pairData);

            if (showCombinedPrice) {
                addCombinedPricesToChart(chart, pairData);
            }

            if (showPixelSpread) {
                addPixelSpreadToZScoreChart(chart, pairData);
            }
        }

        return BitmapEncoder.getBufferedImage(chart);
    }

    /**
     * Создает базовый чарт для комбинирования компонентов (без Z-Score)
     */
    private XYChart createBaseCombinedChart(PairData pairData) {
        List<ZScoreParam> history = pairData.getZScoreHistory();

        List<Long> timestamps;
        if (history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста для пары {}, используем текущее время", pairData.getPairName());
            timestamps = Collections.singletonList(System.currentTimeMillis());
        } else {
            timestamps = history.stream()
                    .map(ZScoreParam::getTimestamp)
                    .collect(Collectors.toList());
        }

        List<Date> timeAxis = timestamps.stream().map(Date::new).collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Combined Chart: LONG (" + pairData.getLongTicker() + ") - SHORT (" + pairData.getShortTicker() + ")")
                .xAxisTitle("Time").yAxisTitle("Values")
                .build();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDatePattern("HH:mm");
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setYAxisTicksVisible(false);
        chart.getStyler().setYAxisTitleVisible(false);

        // Добавляем точку входа если есть
        long entryTimestamp = pairData.getEntryTime() > 0 ? pairData.getEntryTime() : pairData.getTimestamp();
        long historyStart = timestamps.get(0);
        long historyEnd = timestamps.get(timestamps.size() - 1);

        boolean inRange = entryTimestamp > 0 && entryTimestamp >= historyStart && entryTimestamp <= historyEnd;

        if (inRange) {
            Date entryDate = new Date(entryTimestamp);
            List<Date> lineX = Arrays.asList(entryDate, entryDate);
            // ИСПРАВЛЕНО: Используем диапазон Z-Score для совместимости с добавляемыми компонентами
            List<Double> lineY = Arrays.asList(-3.0, 3.0);

            XYSeries entryLine = chart.addSeries("Entry", lineX, lineY);
            entryLine.setLineColor(Color.BLUE);
            entryLine.setMarker(new None());
            entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));
            log.debug("✅ Линия входа добавлена на комбинированный чарт с Z-Score диапазоном");
        }

        return chart;
    }

    /**
     * Добавляет новую точку пиксельного спреда
     */
    public void addCurrentPixelSpreadPoint(PairData pairData) {
        pixelSpreadService.addCurrentPixelSpreadPoint(pairData);
    }

    public BufferedImage createPixelSpreadChartWithProfit(PairData pairData, boolean showProfit, boolean showEntryPoint) {
        return createPixelSpreadChartInternal(pairData, showProfit, showEntryPoint);
    }

    /**
     * Создает график пиксельного спреда
     */
    public BufferedImage createPixelSpreadChart(PairData pairData) {
        return createPixelSpreadChartInternal(pairData, false, false);
    }

    private BufferedImage createPixelSpreadChartInternal(PairData pairData, boolean showProfit, boolean showEntryPoint) {
        List<PixelSpreadHistoryItem> pixelHistory = pairData.getPixelSpreadHistory();

        if (pixelHistory == null || pixelHistory.isEmpty()) {
            log.warn("📊 История пиксельного спреда пуста для пары {}", pairData.getPairName());
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        // Сортируем по времени
        pixelHistory.sort(Comparator.comparing(PixelSpreadHistoryItem::getTimestamp));

        List<Date> timeAxis = pixelHistory.stream()
                .map(item -> new Date(item.getTimestamp()))
                .collect(Collectors.toList());
        List<Double> pixelDistances = pixelHistory.stream()
                .map(PixelSpreadHistoryItem::getPixelDistance)
                .collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(1920).height(720)
                .title("Pixel Spread Chart: LONG (" + pairData.getLongTicker() + ") - SHORT (" + pairData.getShortTicker() + ")")
                .xAxisTitle("Time").yAxisTitle("Pixel Distance")
                .build();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDatePattern("HH:mm");
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setYAxisTicksVisible(false);
        chart.getStyler().setYAxisTitleVisible(false);

        XYSeries pixelSeries = chart.addSeries("Pixel Distance", timeAxis, pixelDistances);
        pixelSeries.setLineColor(Color.BLUE);
        pixelSeries.setMarker(new None());
        pixelSeries.setLineStyle(new BasicStroke(2.0f));

        // Добавляем профит если нужно
        if (showProfit) {
            addProfitToChart(chart, pairData);
        }

        // Добавляем точку входа если нужно
        if (showEntryPoint) {
            addEntryPointToPriceChart(chart, pairData, timeAxis, pixelDistances);
        }

        log.debug("✅ График пиксельного спреда создан с {} точками для пары {} (профит: {}, точка входа: {})",
                pixelHistory.size(), pairData.getPairName(), showProfit, showEntryPoint);

        return BitmapEncoder.getBufferedImage(chart);
    }
}

