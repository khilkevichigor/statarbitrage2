package com.example.core.services;

import com.example.shared.dto.Candle;
import com.example.shared.dto.PixelSpreadHistoryItem;
import com.example.shared.dto.ProfitHistoryItem;
import com.example.shared.dto.ZScoreParam;
import com.example.shared.models.Pair;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private final PixelSpreadService pixelSpreadService;
    
    // Константы для унифицированного стиля чартов
    private static final int CHART_WIDTH = 1920;
    private static final int CHART_HEIGHT = 720;
    private static final int MAX_TIME_TICKS = 10;
    
    /**
     * Применяет унифицированный стиль ко всем чартам
     */
    private void applyUnifiedChartStyle(XYChart chart, List<Date> timeAxis) {
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setDatePattern(getOptimalDatePattern(timeAxis));
        chart.getStyler().setXAxisTickMarkSpacingHint(Math.max(50, timeAxis.size() / MAX_TIME_TICKS));
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setYAxisTicksVisible(false);
        chart.getStyler().setYAxisTitleVisible(false);
        chart.getStyler().setXAxisTitleVisible(false); // Убираем все подписи "Time"
    }

    public BufferedImage createZScoreChart(Pair tradingPair, boolean showEma, int emaPeriod, boolean showStochRsi, boolean showProfit, boolean showCombinedPrice, boolean showPixelSpread, boolean showEntryPoint) {
        log.debug("Создание расширенного Z-Score графика для пары: {} (EMA: {}, период: {}, StochRSI: {}, Profit: {}, CombinedPrice: {}, PixelSpread: {}, EntryPoint: {})",
                tradingPair.getPairName(), showEma, emaPeriod, showStochRsi, showProfit, showCombinedPrice, showPixelSpread, showEntryPoint);

        XYChart chart = buildEnhancedZScoreChart(tradingPair, showEma, emaPeriod, showStochRsi, showProfit, showCombinedPrice, showPixelSpread, showEntryPoint);

        return BitmapEncoder.getBufferedImage(chart);
    }

    private XYChart buildEnhancedZScoreChart(Pair tradingPair, boolean showEma, int emaPeriod, boolean showStochRsi, boolean showProfit, boolean showCombinedPrice, boolean showPixelSpread, boolean showEntryPoint) {
        // Всегда используем базовый Z-Score чарт (история Z-Score определяет период)
        XYChart chart = buildBasicZScoreChart(tradingPair, showEntryPoint);

        List<ZScoreParam> history = tradingPair.getZScoreHistory();

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
            addProfitToChart(chart, tradingPair);
        }

        if (showCombinedPrice) {
            addSynchronizedPricesToChart(chart, tradingPair);
        }

        if (showPixelSpread) {
            addPixelSpreadToZScoreChart(chart, tradingPair);
        }

        return chart;
    }

    private XYChart buildBasicZScoreChart(Pair tradingPair, boolean showEntryPoint) {
        List<ZScoreParam> history = tradingPair.getZScoreHistory();

        List<Long> timestamps;
        List<Double> zScores;

        if (history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста для пары {}, создаем минимальные данные", tradingPair.getPairName());
            timestamps = Collections.singletonList(System.currentTimeMillis());
            zScores = Collections.singletonList(tradingPair.getZScoreCurrent() != null ? tradingPair.getZScoreCurrent().doubleValue() : 0.0);
        } else {
            timestamps = history.stream()
                    .map(ZScoreParam::getTimestamp)
                    .collect(Collectors.toList());
            zScores = history.stream()
                    .map(ZScoreParam::getZscore)
                    .collect(Collectors.toList());

            log.debug("Используем реальную историю Z-Score: {} точек для пары {}", history.size(), tradingPair.getPairName());
        }

        log.debug("Временной диапазон графика от: {} - до: {}",
                new Date(timestamps.get(0)), new Date(timestamps.get(timestamps.size() - 1)));
        log.debug("Текущий Z-Score: {}", tradingPair.getZScoreCurrent());

        if (timestamps.size() != zScores.size()) {
            log.warn("⚠️ Неверные входные данные для построения Z-графика");
            throw new IllegalArgumentException("Timestamps and zScores lists must have the same size");
        }

        List<Date> timeAxis = timestamps.stream().map(Date::new).collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title("Z-Score LONG (" + tradingPair.getLongTicker() + ") - SHORT (" + tradingPair.getShortTicker() + ")")
                .xAxisTitle("").yAxisTitle("")
                .build();

        applyUnifiedChartStyle(chart, timeAxis);

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
            long entryTimestamp = tradingPair.getEntryTime() != null ?
                    tradingPair.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                    (tradingPair.getTimestamp() != null ? tradingPair.getTimestamp() : System.currentTimeMillis());
            long historyStart = timestamps.get(0);
            long historyEnd = timestamps.get(timestamps.size() - 1);

            log.debug("Проверка линии входа: entryTime={}, historyStart={}, historyEnd={}",
                    new Date(entryTimestamp), new Date(historyStart), new Date(historyEnd));
            log.debug("PairData: entryTime={}, timestamp={}", tradingPair.getEntryTime(), tradingPair.getTimestamp());

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

                    double entryZScore = tradingPair.getZScoreEntry() != null ? tradingPair.getZScoreEntry().doubleValue() : 0.0;
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

                double entryZScore = tradingPair.getZScoreEntry() != null ? tradingPair.getZScoreEntry().doubleValue() : 0.0;
                List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
                List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

                XYSeries entryHorizontalLine = chart.addSeries("Entry Z-Score (approx)", horizontalLineX, horizontalLineY);
                entryHorizontalLine.setLineColor(Color.ORANGE);
                entryHorizontalLine.setMarker(new None());
                entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

                log.debug("✅ Приблизительная линия входа добавлена на графике");
            } else {
                log.debug("⚠️ Время входа не задано (0) - линия входа не будет показана");
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

    private void addProfitToChart(XYChart chart, Pair tradingPair) {
        List<ProfitHistoryItem> profitHistory = tradingPair.getProfitHistory();
        if (profitHistory == null || profitHistory.isEmpty()) {
            log.debug("📊 История профита пуста для пары {}, график профита не будет добавлен.", tradingPair.getPairName());
            return;
        }

        long entryTimestamp = tradingPair.getEntryTime() != null ?
                tradingPair.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                (tradingPair.getTimestamp() != null ? tradingPair.getTimestamp() : System.currentTimeMillis());

        List<ProfitHistoryItem> filteredProfitHistory = profitHistory.stream()
                .filter(item -> item.getTimestamp() >= entryTimestamp)
                .collect(Collectors.toList());

        if (filteredProfitHistory.isEmpty()) {
            log.debug("📊 Нет данных профита с момента входа для пары {}, используя все данные", tradingPair.getPairName());
            filteredProfitHistory = profitHistory;
        }

        if (filteredProfitHistory.isEmpty()) {
            log.debug("📊 История профита все еще пуста после всех проверок для пары {}", tradingPair.getPairName());
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

    public BufferedImage createPriceChart(Pair tradingPair) {
        return createPriceChart(tradingPair, false);
    }

    public BufferedImage createPriceChartWithProfit(Pair tradingPair, boolean showPixelSpread, boolean showProfit, boolean showEntryPoint) {
        return createPriceChartInternal(tradingPair, showPixelSpread, showProfit, showEntryPoint);
    }

    public BufferedImage createPriceChart(Pair tradingPair, boolean showPixelSpread) {
        return createPriceChartInternal(tradingPair, showPixelSpread, false, false);
    }

    private BufferedImage createPriceChartInternal(Pair tradingPair, boolean showPixelSpread, boolean showProfit, boolean showEntryPoint) {
        String longTicker = tradingPair.getLongTicker();
        String shortTicker = tradingPair.getShortTicker();

        List<Candle> longCandles = tradingPair.getLongTickerCandles();
        List<Candle> shortCandles = tradingPair.getShortTickerCandles();
        List<ZScoreParam> history = tradingPair.getZScoreHistory();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("Не найдены свечи для тикеров {} или {}", longTicker, shortTicker);
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        log.info("📊 Создание графика для пары {}/{}. LONG: {} свечей, SHORT: {} свечей, Z-Score история: {} записей",
                longTicker, shortTicker, longCandles.size(), shortCandles.size(),
                history != null ? history.size() : 0);

        // Проверяем исходный порядок свечей
        validateCandleOrder(longTicker, longCandles);
        validateCandleOrder(shortTicker, shortCandles);

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        log.info("📈 После сортировки - LONG диапазон: {} - {}, SHORT диапазон: {} - {}",
                longCandles.get(0).getTimestamp(), longCandles.get(longCandles.size() - 1).getTimestamp(),
                shortCandles.get(0).getTimestamp(), shortCandles.get(shortCandles.size() - 1).getTimestamp());

        // Не синхронизируем Price чарт с Z-Score историей - показываем все доступные свечи
        log.info("📊 Price чарт будет отображать все доступные свечи: LONG {} свечей, SHORT {} свечей",
                longCandles.size(), shortCandles.size());

        // Дата и цены (используем все доступные свечи)
        List<Date> timeLong = longCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = longCandles.stream().map(Candle::getClose).toList();

        List<Date> timeShort = shortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = shortCandles.stream().map(Candle::getClose).toList();

        // График 1: первая монета (long)
        XYChart topChart = new XYChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title("Price Chart: LONG (" + longTicker + ") - SHORT (" + shortTicker + ")")
                .xAxisTitle("").yAxisTitle("")
                .build();
        
        applyUnifiedChartStyle(topChart, timeLong);

        XYSeries longSeries = topChart.addSeries("LONG: " + longTicker + " (current " + tradingPair.getLongTickerCurrentPrice() + ")", timeLong, longPrices);
        longSeries.setLineColor(Color.GREEN);
        longSeries.setMarker(new None());

        // График 2: вторая монета (short)
        XYChart bottomChart = new XYChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title("Price Chart: LONG (" + longTicker + ") - SHORT (" + shortTicker + ")")
                .xAxisTitle("").yAxisTitle("")
                .build();
        
        applyUnifiedChartStyle(bottomChart, timeShort);

        XYSeries shortSeries = bottomChart.addSeries("SHORT: " + shortTicker + " (current " + tradingPair.getShortTickerCurrentPrice() + ")", timeShort, shortPrices);
        shortSeries.setLineColor(Color.RED);
        shortSeries.setMarker(new None());

        // Добавляем пиксельный спред если нужно
        if (showPixelSpread) {
            addPixelSpreadToPriceChart(topChart, tradingPair, timeLong, longPrices);
            addPixelSpreadToPriceChart(bottomChart, tradingPair, timeShort, shortPrices);
        }

        // Добавляем профит если нужно
        if (showProfit) {
            addProfitToChart(topChart, tradingPair);
            addProfitToChart(bottomChart, tradingPair);
        }

        // Добавляем точку входа если нужно
        if (showEntryPoint) {
            addEntryPointToPriceChart(topChart, tradingPair, timeLong, longPrices);
            addEntryPointToPriceChart(bottomChart, tradingPair, timeShort, shortPrices);
        }

        // Объединение 2 графиков
        BufferedImage topImage = BitmapEncoder.getBufferedImage(topChart);
        BufferedImage bottomImage = BitmapEncoder.getBufferedImage(bottomChart);

        BufferedImage combinedImage = new BufferedImage(CHART_WIDTH, CHART_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combinedImage.createGraphics();

        // Нарисовать верхний график (long) полностью
        g2.drawImage(topImage, 0, 0, null);

        // Установить прозрачность 50% и наложить нижний график (short)
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g2.drawImage(bottomImage, 0, 0, null);

        g2.dispose();

        return combinedImage;
    }

    private void addCombinedPricesToChart(XYChart chart, Pair tradingPair) {
        String longTicker = tradingPair.getLongTicker();
        String shortTicker = tradingPair.getShortTicker();

        List<Candle> longCandles = tradingPair.getLongTickerCandles();
        List<Candle> shortCandles = tradingPair.getShortTickerCandles();
        List<ZScoreParam> history = tradingPair.getZScoreHistory();

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
        pixelSpreadService.calculatePixelSpreadIfNeeded(tradingPair);
    }

    /**
     * Добавляет наложенные цены на чарт синхронизированно с Z-Score периодом
     */
    private void addSynchronizedPricesToChart(XYChart chart, Pair tradingPair) {
        String longTicker = tradingPair.getLongTicker();
        String shortTicker = tradingPair.getShortTicker();

        List<Candle> longCandles = tradingPair.getLongTickerCandles();
        List<Candle> shortCandles = tradingPair.getShortTickerCandles();
        List<ZScoreParam> history = tradingPair.getZScoreHistory();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Не найдены данные для синхронизированных цен на чарт: longCandles={}, shortCandles={}",
                    longCandles != null ? longCandles.size() : "null",
                    shortCandles != null ? shortCandles.size() : "null");
            return;
        }

        if (history == null || history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста - невозможно синхронизировать цены");
            return;
        }

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Получаем временной диапазон Z-Score истории
        long zScoreStartTime = history.get(0).getTimestamp();
        long zScoreEndTime = history.get(history.size() - 1).getTimestamp();

        log.info("📊 Синхронизируем цены с Z-Score периодом: {} - {} ({} записей Z-Score)",
                new Date(zScoreStartTime), new Date(zScoreEndTime), history.size());

        // Фильтруем свечи по временному диапазону Z-Score истории
        List<Candle> filteredLongCandles = longCandles.stream()
                .filter(c -> c.getTimestamp() >= zScoreStartTime && c.getTimestamp() <= zScoreEndTime)
                .collect(Collectors.toList());
        
        List<Candle> filteredShortCandles = shortCandles.stream()
                .filter(c -> c.getTimestamp() >= zScoreStartTime && c.getTimestamp() <= zScoreEndTime)
                .collect(Collectors.toList());

        if (filteredLongCandles.isEmpty() || filteredShortCandles.isEmpty()) {
            log.warn("⚠️ Нет свечей в периоде Z-Score истории для синхронизации");
            return;
        }

        // Получение времени и цен для синхронизированного периода
        List<Date> timeLong = filteredLongCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> longPrices = filteredLongCandles.stream().map(Candle::getClose).toList();

        List<Date> timeShort = filteredShortCandles.stream().map(c -> new Date(c.getTimestamp())).toList();
        List<Double> shortPrices = filteredShortCandles.stream().map(Candle::getClose).toList();

        // Найти диапазон Z-Score для масштабирования цен
        double minZScore = history.stream().mapToDouble(ZScoreParam::getZscore).min().orElse(-3.0);
        double maxZScore = history.stream().mapToDouble(ZScoreParam::getZscore).max().orElse(3.0);
        double zRange = maxZScore - minZScore;

        // Найти диапазон цен для нормализации (только для синхронизированного периода)
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

        log.info("✅ Добавляем синхронизированные цены на Z-Score чарт: LONG {} точек, SHORT {} точек",
                scaledLongPrices.size(), scaledShortPrices.size());

        // Добавляем long цены как полупрозрачную зеленую линию
        XYSeries longPriceSeries = chart.addSeries("LONG Price (sync): " + longTicker, timeLong, scaledLongPrices);
        longPriceSeries.setLineColor(new Color(0, 255, 0, 120)); // Полупрозрачный зеленый
        longPriceSeries.setMarker(new None());
        longPriceSeries.setLineStyle(new BasicStroke(1.5f));

        // Добавляем short цены как полупрозрачную красную линию
        XYSeries shortPriceSeries = chart.addSeries("SHORT Price (sync): " + shortTicker, timeShort, scaledShortPrices);
        shortPriceSeries.setLineColor(new Color(255, 0, 0, 120)); // Полупрозрачный красный
        shortPriceSeries.setMarker(new None());
        shortPriceSeries.setLineStyle(new BasicStroke(1.5f));

        log.debug("✅ Синхронизированные цены успешно добавлены на Z-Score чарт!");
    }

    /**
     * Создает Z-Score чарт синхронизированный с полным периодом свечей для наложенных цен
     */
    private XYChart buildSynchronizedZScoreChart(Pair tradingPair, boolean showEntryPoint) {
        List<Candle> longCandles = tradingPair.getLongTickerCandles();
        List<Candle> shortCandles = tradingPair.getShortTickerCandles();
        List<ZScoreParam> originalHistory = tradingPair.getZScoreHistory();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Нет свечей для синхронизации Z-Score с наложенными ценами");
            return buildBasicZScoreChart(tradingPair, showEntryPoint);
        }

        // Сортируем свечи
        longCandles.sort(Comparator.comparing(Candle::getTimestamp));
        shortCandles.sort(Comparator.comparing(Candle::getTimestamp));

        // Находим общие временные точки
        int minSize = Math.min(longCandles.size(), shortCandles.size());
        
        List<Long> extendedTimestamps = new ArrayList<>();
        List<Double> extendedZScores = new ArrayList<>();
        
        log.info("🔄 Создаем синхронизированный Z-Score для {} свечей (Long: {}, Short: {})", 
                minSize, longCandles.size(), shortCandles.size());

        // Вычисляем Z-Score для всех доступных свечей
        for (int i = 0; i < minSize; i++) {
            long timestamp = longCandles.get(i).getTimestamp();
            double longPrice = longCandles.get(i).getClose();
            double shortPrice = shortCandles.get(i).getClose();
            
            // Простое вычисление Z-Score как нормализованной разности цен
            double spread = longPrice - shortPrice;
            
            // Нормализуем в диапазон [-3, 3] для совместимости
            double normalizedZScore = Math.max(-3.0, Math.min(3.0, spread / Math.max(longPrice, shortPrice) * 100));
            
            extendedTimestamps.add(timestamp);
            extendedZScores.add(normalizedZScore);
        }

        if (extendedTimestamps.isEmpty()) {
            log.warn("⚠️ Не удалось вычислить синхронизированные Z-Score данные");
            return buildBasicZScoreChart(tradingPair, showEntryPoint);
        }

        List<Date> timeAxis = extendedTimestamps.stream().map(Date::new).collect(Collectors.toList());

        log.info("✅ Синхронизированный Z-Score: {} точек, диапазон: {} - {}",
                extendedZScores.size(), timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));

        // Создаем чарт
        XYChart chart = new XYChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title("Synchronized Z-Score LONG (" + tradingPair.getLongTicker() + ") - SHORT (" + tradingPair.getShortTicker() + ")")
                .xAxisTitle("").yAxisTitle("")
                .build();

        applyUnifiedChartStyle(chart, timeAxis);

        // Добавляем Z-Score линию
        XYSeries zSeries = chart.addSeries("Z-Score (Synchronized)", timeAxis, extendedZScores);
        zSeries.setLineColor(Color.MAGENTA);
        zSeries.setMarker(new None());

        // Добавляем горизонтальные линии
        addHorizontalLine(chart, timeAxis, 3.0, Color.BLUE);
        addHorizontalLine(chart, timeAxis, 2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, 1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, 0.0, Color.BLACK);
        addHorizontalLine(chart, timeAxis, -1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, -2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, -3.0, Color.BLUE);

        // Добавляем точку входа если нужно
        if (showEntryPoint) {
            addEntryPointToSynchronizedChart(chart, tradingPair, timeAxis, extendedZScores);
        }

        return chart;
    }

    /**
     * Добавляет точку входа на синхронизированный Z-Score чарт
     */
    private void addEntryPointToSynchronizedChart(XYChart chart, Pair tradingPair, List<Date> timeAxis, List<Double> zScores) {
        long entryTimestamp = tradingPair.getEntryTime() != null ? 
            tradingPair.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 
            (tradingPair.getTimestamp() != null ? tradingPair.getTimestamp() : System.currentTimeMillis());

        if (entryTimestamp <= 0 || timeAxis.isEmpty() || zScores.isEmpty()) {
            log.debug("⚠️ Недостаточно данных для отображения точки входа на синхронизированном Z-Score чарте");
            return;
        }

        long historyStart = timeAxis.get(0).getTime();
        long historyEnd = timeAxis.get(timeAxis.size() - 1).getTime();
        
        Date entryDate;
        Color lineColor;
        String seriesName;
        
        boolean inRange = entryTimestamp >= historyStart && entryTimestamp <= historyEnd;
        if (inRange) {
            entryDate = new Date(entryTimestamp);
            lineColor = Color.BLUE;
            seriesName = "Entry Point (Sync)";
            log.debug("🎯 Точка входа попадает в синхронизированный диапазон Z-Score");
        } else if (entryTimestamp < historyStart) {
            entryDate = new Date(historyStart);
            lineColor = Color.ORANGE;
            seriesName = "Entry Point (Sync, Start)";
            log.debug("🎯 Точка входа до начала синхронизированного диапазона");
        } else {
            entryDate = new Date(historyEnd);
            lineColor = Color.ORANGE;
            seriesName = "Entry Point (Sync, End)";
            log.debug("🎯 Точка входа после окончания синхронизированного диапазона");
        }

        // Вертикальная линия входа
        double minY = zScores.stream().min(Double::compareTo).orElse(-3.0);
        double maxY = zScores.stream().max(Double::compareTo).orElse(3.0);
        
        List<Date> verticalLineX = Arrays.asList(entryDate, entryDate);
        List<Double> verticalLineY = Arrays.asList(minY, maxY);

        XYSeries entryVerticalLine = chart.addSeries(seriesName, verticalLineX, verticalLineY);
        entryVerticalLine.setLineColor(lineColor);
        entryVerticalLine.setMarker(new None());
        entryVerticalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

        // Горизонтальная линия Z-Score входа
        if (tradingPair.getZScoreEntry() != null) {
            double entryZScore = tradingPair.getZScoreEntry().doubleValue();
            List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
            List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

            XYSeries entryHorizontalLine = chart.addSeries(seriesName + " Z-Level", horizontalLineX, horizontalLineY);
            entryHorizontalLine.setLineColor(lineColor);
            entryHorizontalLine.setMarker(new None());
            entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));
        }

        log.debug("✅ Точка входа добавлена на синхронизированный Z-Score чарт");
    }

    /**
     * Добавляет пиксельный спред на Z-Score чарт
     */
    private void addPixelSpreadToZScoreChart(XYChart chart, Pair tradingPair) {
        List<PixelSpreadHistoryItem> pixelHistory = tradingPair.getPixelSpreadHistory();

        if (pixelHistory == null || pixelHistory.isEmpty()) {
            log.warn("📊 История пиксельного спреда пуста для пары {}, не можем добавить на Z-Score чарт", tradingPair.getPairName());
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
        List<ZScoreParam> history = tradingPair.getZScoreHistory();
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
    private void addPixelSpreadToPriceChart(XYChart chart, Pair tradingPair, List<Date> priceTimeAxis, List<Double> prices) {
        List<PixelSpreadHistoryItem> pixelHistory = tradingPair.getPixelSpreadHistory();

        if (pixelHistory == null || pixelHistory.isEmpty()) {
            log.warn("📊 История пиксельного спреда пуста для пары {}, не можем добавить на Price чарт", tradingPair.getPairName());
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
    private void addEntryPointToPriceChart(XYChart chart, Pair tradingPair, List<Date> timeAxis, List<Double> prices) {
        long entryTimestamp = tradingPair.getEntryTime() != null ?
                tradingPair.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                (tradingPair.getTimestamp() != null ? tradingPair.getTimestamp() : System.currentTimeMillis());

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
    private void calculateAndSavePixelSpread(Pair tradingPair, List<Date> timeLong, List<Double> scaledLongPrices,
                                             List<Date> timeShort, List<Double> scaledShortPrices) {
        log.debug("🔢 Начинаем вычисление пиксельного спреда для пары {}", tradingPair.getPairName());

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
                tradingPair.addPixelSpreadPoint(pixelSpreadItem);

                log.trace("🔢 Timestamp: {}, Long: {} px, Short: {} px, Distance: {} px",
                        new Date(timestamp), Math.round(longPixelY), Math.round(shortPixelY), Math.round(pixelDistance));
            }
        }

        log.debug("✅ Пиксельный спред вычислен и сохранен. Всего точек: {}",
                tradingPair.getPixelSpreadHistory().size());
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
    public void calculatePixelSpreadIfNeeded(Pair tradingPair) {
        if (tradingPair.getPixelSpreadHistory().isEmpty()) {
            log.debug("🔢 Пиксельный спред не вычислен, вычисляем независимо от чекбокса объединенных цен");
            calculatePixelSpreadForPair(tradingPair);
        }
    }

    /**
     * Вычисляет пиксельный спред для пары
     */
    private void calculatePixelSpreadForPair(Pair tradingPair) {
        String longTicker = tradingPair.getLongTicker();
        String shortTicker = tradingPair.getShortTicker();

        List<Candle> longCandles = tradingPair.getLongTickerCandles();
        List<Candle> shortCandles = tradingPair.getShortTickerCandles();
        List<ZScoreParam> history = tradingPair.getZScoreHistory();

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
        calculateAndSavePixelSpread(tradingPair, timeLong, scaledLongPrices, timeShort, scaledShortPrices);
    }

    /**
     * Создает комбинированный чарт с выбранными компонентами
     */
    public BufferedImage createCombinedChart(Pair tradingPair, boolean showZScore, boolean showCombinedPrice,
                                             boolean showPixelSpread, boolean showEma, int emaPeriod,
                                             boolean showStochRsi, boolean showProfit, boolean showEntryPoint) {
        log.debug("🎨 Создание комбинированного чарта для пары: {} (ZScore: {}, Price: {}, PixelSpread: {}, EMA: {}, StochRSI: {}, Profit: {})",
                tradingPair.getPairName(), showZScore, showCombinedPrice, showPixelSpread, showEma, showStochRsi, showProfit);

        // Если выбран только один тип чарта, используем специализированные методы
        if (showZScore && !showCombinedPrice && !showPixelSpread) {
            return createZScoreChart(tradingPair, showEma, emaPeriod, showStochRsi, showProfit, false, false, showEntryPoint);
        } else if (showCombinedPrice && !showZScore && !showPixelSpread) {
            return createPriceChartInternal(tradingPair, false, false, showEntryPoint);
        } else if (showPixelSpread && !showZScore && !showCombinedPrice) {
            return createPixelSpreadChartInternal(tradingPair, false, showEntryPoint);
        }

        // Для комбинированного чарта используем Z-Score как базу
        XYChart chart;

        if (showZScore) {
            // Если Z-Score выбран, используем его как основу
            // Создаем Z-Score чарт без синхронизации наложенных цен
            chart = buildEnhancedZScoreChart(tradingPair, showEma, emaPeriod, showStochRsi, showProfit, false, showPixelSpread, showEntryPoint);
            
            // Добавляем наложенные цены синхронизированно с Z-Score периодом
            if (showCombinedPrice) {
                addSynchronizedPricesToChart(chart, tradingPair);
            }
        } else {
            // Если Z-Score не выбран, создаем базовый чарт для других компонентов
            chart = createBaseCombinedChart(tradingPair);

            if (showCombinedPrice) {
                addCombinedPricesToChart(chart, tradingPair);
            }

            if (showPixelSpread) {
                addPixelSpreadToZScoreChart(chart, tradingPair);
            }
        }

        return BitmapEncoder.getBufferedImage(chart);
    }

    /**
     * Создает базовый чарт для комбинирования компонентов (без Z-Score)
     */
    private XYChart createBaseCombinedChart(Pair tradingPair) {
        List<ZScoreParam> history = tradingPair.getZScoreHistory();

        List<Long> timestamps;
        if (history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста для пары {}, используем текущее время", tradingPair.getPairName());
            timestamps = Collections.singletonList(System.currentTimeMillis());
        } else {
            timestamps = history.stream()
                    .map(ZScoreParam::getTimestamp)
                    .collect(Collectors.toList());
        }

        List<Date> timeAxis = timestamps.stream().map(Date::new).collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title("Combined Chart: LONG (" + tradingPair.getLongTicker() + ") - SHORT (" + tradingPair.getShortTicker() + ")")
                .xAxisTitle("").yAxisTitle("")
                .build();

        applyUnifiedChartStyle(chart, timeAxis);

        // Добавляем точку входа если есть
        long entryTimestamp = tradingPair.getEntryTime() != null ?
                tradingPair.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                (tradingPair.getTimestamp() != null ? tradingPair.getTimestamp() : System.currentTimeMillis());
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
    public void addCurrentPixelSpreadPoint(Pair tradingPair) {
        pixelSpreadService.addCurrentPixelSpreadPoint(tradingPair);
    }

    public BufferedImage createPixelSpreadChartWithProfit(Pair tradingPair, boolean showProfit, boolean showEntryPoint) {
        return createPixelSpreadChartInternal(tradingPair, showProfit, showEntryPoint);
    }

    /**
     * Создает график пиксельного спреда
     */
    public BufferedImage createPixelSpreadChart(Pair tradingPair) {
        return createPixelSpreadChartInternal(tradingPair, false, false);
    }

    private BufferedImage createPixelSpreadChartInternal(Pair tradingPair, boolean showProfit, boolean showEntryPoint) {
        List<PixelSpreadHistoryItem> pixelHistory = tradingPair.getPixelSpreadHistory();

        if (pixelHistory == null || pixelHistory.isEmpty()) {
            log.warn("📊 История пиксельного спреда пуста для пары {}", tradingPair.getPairName());
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
                .width(CHART_WIDTH).height(CHART_HEIGHT)
                .title("Pixel Spread Chart: LONG (" + tradingPair.getLongTicker() + ") - SHORT (" + tradingPair.getShortTicker() + ")")
                .xAxisTitle("").yAxisTitle("")
                .build();

        applyUnifiedChartStyle(chart, timeAxis);

        XYSeries pixelSeries = chart.addSeries("Pixel Distance", timeAxis, pixelDistances);
        pixelSeries.setLineColor(Color.BLUE);
        pixelSeries.setMarker(new None());
        pixelSeries.setLineStyle(new BasicStroke(2.0f));

        // Добавляем профит если нужно
        if (showProfit) {
            addProfitToChart(chart, tradingPair);
        }

        // Добавляем точку входа если нужно
        if (showEntryPoint) {
            addEntryPointToPriceChart(chart, tradingPair, timeAxis, pixelDistances);
        }

        log.debug("✅ График пиксельного спреда создан с {} точками для пары {} (профит: {}, точка входа: {})",
                pixelHistory.size(), tradingPair.getPairName(), showProfit, showEntryPoint);

        return BitmapEncoder.getBufferedImage(chart);
    }

    /**
     * Создает чарт нормализованных цен с подсчетом и отображением пересечений
     *
     * @param longCandles        свечи для long позиции
     * @param shortCandles       свечи для short позиции
     * @param pairName           название пары для заголовка
     * @param intersectionsCount количество найденных пересечений
     * @param saveToProject      флаг сохранения в корень проекта
     * @return BufferedImage созданного чарта
     */
    public BufferedImage createNormalizedPriceIntersectionsChart(List<Candle> longCandles, List<Candle> shortCandles,
                                                                 String pairName, int intersectionsCount, boolean saveToProject) {
        log.info("📊 Создание чарта нормализованных цен с пересечениями для пары: {} (пересечений: {})", pairName, intersectionsCount);

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Нет данных свечей для создания чарта пары {}", pairName);
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        int minSize = Math.min(longCandles.size(), shortCandles.size());
        if (minSize < 2) {
            log.warn("⚠️ Недостаточно данных для создания чарта пары {}: minSize={}", pairName, minSize);
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        try {
            // Нормализация цен (используем тот же алгоритм что в PriceIntersectionService)
            double[] normalizedLongPrices = normalizePricesForChart(longCandles, minSize);
            double[] normalizedShortPrices = normalizePricesForChart(shortCandles, minSize);

            // Временные метки
            List<Date> timeAxis = new ArrayList<>();
            for (int i = 0; i < minSize; i++) {
                timeAxis.add(new Date(longCandles.get(i).getTimestamp()));
            }

            // Создаем чарт
            XYChart chart = new XYChartBuilder()
                    .width(CHART_WIDTH).height(CHART_HEIGHT)
                    .title(String.format("Нормализованные цены: %s (Пересечений: %d из %d точек)",
                            pairName, intersectionsCount, minSize))
                    .xAxisTitle("").yAxisTitle("") // Убираем подписи осей
                    .build();

            applyUnifiedChartStyle(chart, timeAxis);

            // Добавляем серии данных
            List<Double> longPricesList = Arrays.stream(normalizedLongPrices).boxed().collect(Collectors.toList());
            List<Double> shortPricesList = Arrays.stream(normalizedShortPrices).boxed().collect(Collectors.toList());

            XYSeries longSeries = chart.addSeries("LONG (нормализованная)", timeAxis, longPricesList);
            longSeries.setLineColor(Color.GREEN);
            longSeries.setMarker(new None());
            longSeries.setLineStyle(new BasicStroke(2.0f));

            XYSeries shortSeries = chart.addSeries("SHORT (нормализованная)", timeAxis, shortPricesList);
            shortSeries.setLineColor(Color.RED);
            shortSeries.setMarker(new None());
            shortSeries.setLineStyle(new BasicStroke(2.0f));

            // Добавляем точки пересечений
            addIntersectionPoints(chart, timeAxis, normalizedLongPrices, normalizedShortPrices);

            // Горизонтальные линии убраны для чистоты чарта

            BufferedImage chartImage = BitmapEncoder.getBufferedImage(chart);

            // Добавляем текст с количеством пересечений на изображение
            addIntersectionTextToImage(chartImage, intersectionsCount);

            // Сохраняем в корень проекта если нужно
            if (saveToProject) {
                saveChartToProject(chartImage, pairName, intersectionsCount);
            }

            log.info("✅ Чарт нормализованных цен создан для пары {} с {} пересечениями", pairName, intersectionsCount);
            return chartImage;

        } catch (Exception e) {
            log.error("❌ Ошибка при создании чарта нормализованных цен для пары {}: {}", pairName, e.getMessage(), e);
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
    }

    /**
     * Нормализует цены закрытия для чарта (аналогично PriceIntersectionService)
     */
    private double[] normalizePricesForChart(List<Candle> candles, int size) {
        double[] prices = new double[size];
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        // Извлекаем цены закрытия и находим min/max
        for (int i = 0; i < size; i++) {
            prices[i] = candles.get(i).getClose();
            min = Math.min(min, prices[i]);
            max = Math.max(max, prices[i]);
        }

        // Нормализуем
        double range = max - min;
        if (range == 0) {
            // Все цены одинаковые - возвращаем массив нулей
            return new double[size];
        }

        for (int i = 0; i < size; i++) {
            prices[i] = (prices[i] - min) / range;
        }

        return prices;
    }

    /**
     * Добавляет точки пересечений на чарт
     */
    private void addIntersectionPoints(XYChart chart, List<Date> timeAxis, double[] prices1, double[] prices2) {
        List<Date> intersectionTimes = new ArrayList<>();
        List<Double> intersectionValues = new ArrayList<>();

        if (prices1.length != prices2.length || prices1.length < 2) {
            return;
        }

        // Определяем начальное положение (кто выше)
        boolean firstAboveSecond = prices1[0] > prices2[0];

        // Проходим по всем точкам и ищем пересечения
        for (int i = 1; i < prices1.length; i++) {
            boolean currentFirstAboveSecond = prices1[i] > prices2[i];

            // Если положение изменилось - это пересечение
            if (currentFirstAboveSecond != firstAboveSecond) {
                // Находим приблизительную точку пересечения (среднее значение)
                double intersectionValue = (prices1[i] + prices2[i]) / 2.0;

                intersectionTimes.add(timeAxis.get(i));
                intersectionValues.add(intersectionValue);

                firstAboveSecond = currentFirstAboveSecond;
            }
        }

        if (!intersectionTimes.isEmpty()) {
            XYSeries intersectionSeries = chart.addSeries("Пересечения", intersectionTimes, intersectionValues);
            intersectionSeries.setMarker(SeriesMarkers.CIRCLE);
            intersectionSeries.setMarkerColor(Color.BLUE);
            intersectionSeries.setLineColor(new Color(0, 0, 0, 0)); // Прозрачный цвет линии

            log.info("✅ Добавлено {} точек пересечений на чарт", intersectionTimes.size());
        }
    }

    /**
     * Сохраняет чарт в папку microservices/charts/filter/intersections
     */
    private void saveChartToProject(BufferedImage chartImage, String pairName, int intersectionsCount) {
        try {
            // Создаем путь к папке charts/filter/intersections внутри microservices
            Path chartsDir = Paths.get(System.getProperty("user.dir"), "charts", "filter", "intersections");

            // Создаем директории если они не существуют
            Files.createDirectories(chartsDir);

            // Создаем базовое имя файла без timestamp
            String baseName = pairName.replaceAll("[^a-zA-Z0-9-_]", "_") + "_intersections";
            String fileName = baseName + "_" + intersectionsCount + ".png";
            Path chartPath = chartsDir.resolve(fileName);

            // Удаляем все старые файлы для этой пары (по базовому имени)
            try {
                Files.list(chartsDir)
                        .filter(path -> path.getFileName().toString().startsWith(baseName + "_"))
                        .forEach(oldFile -> {
                            try {
                                Files.delete(oldFile);
                                log.info("🗑️ Удален старый чарт: {}", oldFile.getFileName());
                            } catch (IOException e) {
                                log.warn("⚠️ Не удалось удалить старый чарт {}: {}", oldFile.getFileName(), e.getMessage());
                            }
                        });
            } catch (IOException e) {
                log.warn("⚠️ Ошибка при поиске старых файлов чартов: {}", e.getMessage());
            }

            // Сохраняем новый чарт используя стандартный Java ImageIO
            javax.imageio.ImageIO.write(chartImage, "PNG", chartPath.toFile());

            log.info("✅ Чарт нормализованных цен обновлен: {}", chartPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("❌ Ошибка при сохранении чарта: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ Неожиданная ошибка при сохранении чарта: {}", e.getMessage(), e);
        }
    }

    /**
     * Проверяет хронологический порядок свечей и логирует проблемы
     */
    private void validateCandleOrder(String ticker, List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            return;
        }

        boolean hasTimeOrderIssues = false;
        long prevTimestamp = candles.get(0).getTimestamp();

        for (int i = 1; i < candles.size(); i++) {
            long currentTimestamp = candles.get(i).getTimestamp();
            if (currentTimestamp <= prevTimestamp) {
                if (!hasTimeOrderIssues) {
                    log.warn("❌ {}: нарушение хронологического порядка свечей в ChartService!", ticker);
                    hasTimeOrderIssues = true;
                }
                log.warn("❌ {}: свеча {} (timestamp={}) <= предыдущей {} (timestamp={})",
                        ticker, i, new Date(currentTimestamp), i - 1, new Date(prevTimestamp));
            }
            prevTimestamp = currentTimestamp;
        }

        if (!hasTimeOrderIssues) {
            log.info("✅ {}: ChartService - хронологический порядок {} свечей корректен. Диапазон: {} - {}",
                    ticker, candles.size(),
                    new Date(candles.get(0).getTimestamp()),
                    new Date(candles.get(candles.size() - 1).getTimestamp()));
        } else {
            log.error("❌ {}: КРИТИЧЕСКАЯ ОШИБКА в ChartService - нарушен хронологический порядок свечей! Это приведет к неверным графикам!", ticker);
        }
    }

    /**
     * Определяет оптимальный паттерн даты для оси X в зависимости от временного диапазона
     */
    private String getOptimalDatePattern(List<Date> timeAxis) {
        if (timeAxis == null || timeAxis.size() < 2) {
            return "dd.MM HH:mm";
        }

        try {
            long startTime = timeAxis.get(0).getTime();
            long endTime = timeAxis.get(timeAxis.size() - 1).getTime();
            long durationMs = endTime - startTime;

            // Конвертируем в часы для удобства
            long durationHours = durationMs / (1000 * 60 * 60);

            log.debug("📅 Анализ временного диапазона: {} часов ({} - {})",
                    durationHours, timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));

            // Выбираем паттерн в зависимости от продолжительности
            if (durationHours <= 24) {
                // Меньше суток - показываем часы и минуты
                return "HH:mm";
            } else if (durationHours <= 24 * 7) {
                // Неделя - показываем день и время
                return "dd.MM HH:mm";
            } else if (durationHours <= 24 * 30) {
                // Месяц - показываем день и месяц
                return "dd.MM";
            } else if (durationHours <= 24 * 365) {
                // Год - показываем день и месяц
                return "dd.MM";
            } else {
                // Больше года - показываем месяц и год
                return "MM.yyyy";
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при определении паттерна даты: {}", e.getMessage(), e);
            return "dd.MM HH:mm"; // Паттерн по умолчанию
        }
    }

    /**
     * Добавляет текст с количеством пересечений на изображение чарта
     */
    private void addIntersectionTextToImage(BufferedImage chartImage, int intersectionsCount) {
        try {
            Graphics2D g2d = chartImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Настройки текста (по аналогии с AnalyzeChartIntersectionsService)
            String text = "Intersections: " + intersectionsCount;
            Font font = new Font("Arial", Font.BOLD, 48);  // Крупный жирный текст
            g2d.setFont(font);

            // Измеряем размер текста
            FontMetrics fontMetrics = g2d.getFontMetrics();
            int textWidth = fontMetrics.stringWidth(text);
            int textHeight = fontMetrics.getHeight();

            // Позиционируем в левом верхнем углу с отступом
            int x = 20;
            int y = 70;

            // Рисуем фон для текста (белый прямоугольник с черной рамкой)
            g2d.setColor(Color.WHITE);
            g2d.fillRect(x - 10, y - textHeight + 5, textWidth + 20, textHeight + 10);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(x - 10, y - textHeight + 5, textWidth + 20, textHeight + 10);

            // Рисуем текст
            g2d.setColor(Color.RED);  // Красный цвет для выделения
            g2d.drawString(text, x, y);

            g2d.dispose();

            log.info("✅ Добавлен текст на чарт: '{}'", text);

        } catch (Exception e) {
            log.error("❌ Ошибка при добавлении текста на чарт: {}", e.getMessage(), e);
        }
    }
}

