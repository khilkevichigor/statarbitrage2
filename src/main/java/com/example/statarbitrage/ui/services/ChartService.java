package com.example.statarbitrage.ui.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ProfitHistoryItem;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
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

@Service
@Slf4j
public class ChartService {

    public BufferedImage createZScoreChart(PairData pairData, boolean showEma, int emaPeriod, boolean showStochRsi, boolean showProfit, boolean showCombinedPrice) {
        log.debug("Создание расширенного Z-Score графика для пары: {} (EMA: {}, период: {}, StochRSI: {}, Profit: {}, CombinedPrice: {})",
                pairData.getPairName(), showEma, emaPeriod, showStochRsi, showProfit, showCombinedPrice);

        XYChart chart = buildEnhancedZScoreChart(pairData, showEma, emaPeriod, showStochRsi, showProfit, showCombinedPrice);

        return BitmapEncoder.getBufferedImage(chart);
    }

    private XYChart buildEnhancedZScoreChart(PairData pairData, boolean showEma, int emaPeriod, boolean showStochRsi, boolean showProfit, boolean showCombinedPrice) {
        XYChart chart = buildBasicZScoreChart(pairData);

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

        return chart;
    }

    private XYChart buildBasicZScoreChart(PairData pairData) {
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
            log.warn("📊 История профита пуста для пары {}, график профита не будет добавлен.", pairData.getPairName());
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
            log.warn("📊 История профита все еще пуста после всех проверок для пары {}", pairData.getPairName());
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
        profitSeries.setLineColor(Color.GREEN);
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
        String longTicker = pairData.getLongTicker();
        String shortTicker = pairData.getShortTicker();

        List<Candle> longCandles = pairData.getLongTickerCandles();
        List<Candle> shortCandles = pairData.getShortTickerCandles();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("Не найдены свечи для тикеров {} или {}", longTicker, shortTicker);
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
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

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Не найдены свечи для тикеров {} или {} для наложения на Z-Score чарт", longTicker, shortTicker);
            return;
        }

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Получение времени и цен для long тикера
        List<Date> timeLong = longCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = longCandles.stream().map(Candle::getClose).toList();

        // Получение времени и цен для short тикера  
        List<Date> timeShort = shortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).toList();

        // Найти диапазон Z-Score для масштабирования цен
        List<ZScoreParam> history = pairData.getZScoreHistory();
        double minZScore = history.stream().mapToDouble(ZScoreParam::getZscore).min().orElse(-3.0);
        double maxZScore = history.stream().mapToDouble(ZScoreParam::getZscore).max().orElse(3.0);
        double zRange = maxZScore - minZScore;

        // Найти диапазон цен для нормализации
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
                .collect(Collectors.toList());

        // Нормализация short цен в диапазон Z-Score  
        List<Double> scaledShortPrices = shortPrices.stream()
                .map(price -> shortPriceRange != 0 ? 
                    minZScore + ((price - minShortPrice) / shortPriceRange) * zRange : minZScore)
                .collect(Collectors.toList());

        log.debug("✅ Добавляем объединенные цены на Z-Score чарт: LONG {} точек, SHORT {} точек", 
                scaledLongPrices.size(), scaledShortPrices.size());

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
    }
}
