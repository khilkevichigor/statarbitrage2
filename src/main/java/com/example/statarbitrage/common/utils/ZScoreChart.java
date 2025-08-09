package com.example.statarbitrage.common.utils;

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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public final class ZScoreChart {

    private ZScoreChart() {
    }

    /**
     * Создает BufferedImage с Z-Score графиком для отображения в UI
     *
     * @param pairData данные торговой пары
     * @return BufferedImage с Z-Score графиком
     */
    public static BufferedImage createBufferedImage(PairData pairData) {
        log.debug("Создание BufferedImage Z-Score графика для пары: {}", pairData.getPairName());

        XYChart chart = buildZScoreChart(pairData);

        // Возвращаем BufferedImage для UI
        return BitmapEncoder.getBufferedImage(chart);
    }

    /**
     * Создает расширенный BufferedImage с Z-Score графиком и дополнительными индикаторами
     *
     * @param pairData     данные торговой пары
     * @param showEma      показывать EMA
     * @param emaPeriod    период для EMA
     * @param showStochRsi показывать StochRSI
     * @param showProfit   показывать график профита
     * @return BufferedImage с расширенным графиком
     */
    public static BufferedImage createEnhancedBufferedImage(PairData pairData, boolean showEma, int emaPeriod, boolean showStochRsi, boolean showProfit) {
        log.debug("Создание расширенного Z-Score графика для пары: {} (EMA: {}, период: {}, StochRSI: {}, Profit: {})",
                pairData.getPairName(), showEma, emaPeriod, showStochRsi, showProfit);

        XYChart chart = buildEnhancedZScoreChart(pairData, showEma, emaPeriod, showStochRsi, showProfit);

        // Возвращаем BufferedImage для UI
        return BitmapEncoder.getBufferedImage(chart);
    }

    /**
     * Создает XYChart с Z-Score данными
     *
     * @param pairData данные торговой пары
     * @return настроенный XYChart
     */
    private static XYChart buildZScoreChart(PairData pairData) {
        // Получаем реальную историю Z-Score из PairData
        List<ZScoreParam> history = pairData.getZScoreHistory();

        List<Long> timestamps;
        List<Double> zScores;

        if (history.isEmpty()) {
            // Fallback: если истории нет, создаем минимальную точку с текущими данными
            log.warn("⚠️ История Z-Score пуста для пары {}, создаем минимальные данные", pairData.getPairName());
            timestamps = Collections.singletonList(System.currentTimeMillis());
            zScores = Collections.singletonList(pairData.getZScoreCurrent());
        } else {
            // Используем реальную историю
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
        log.info("Текущий Z-Score: {}", pairData.getZScoreCurrent());

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


        // Горизонтальные уровни
        addHorizontalLine(chart, timeAxis, 3.0, Color.BLUE);
        addHorizontalLine(chart, timeAxis, 2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, 1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, 0.0, Color.BLACK);
        addHorizontalLine(chart, timeAxis, -1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, -2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, -3.0, Color.BLUE);

        // Вертикальная линия входа - используем entryTime (время создания трейда)
        long entryTimestamp = pairData.getEntryTime() > 0 ? pairData.getEntryTime() : pairData.getTimestamp();
        long historyStart = timestamps.get(0);
        long historyEnd = timestamps.get(timestamps.size() - 1);

        log.debug("Проверка линии входа: entryTime={}, historyStart={}, historyEnd={}",
                new Date(entryTimestamp), new Date(historyStart), new Date(historyEnd));
        log.debug("PairData: entryTime={}, timestamp={}", pairData.getEntryTime(), pairData.getTimestamp());

        // Проверяем попадает ли время входа в диапазон истории
        boolean inRange = entryTimestamp > 0 && entryTimestamp >= historyStart && entryTimestamp <= historyEnd;

        if (inRange) {
            log.info("Время входа попадает в диапазон истории - рисуем точную линию входа");

            OptionalInt indexOpt = findClosestIndex(timestamps, entryTimestamp);

            // Рисуем линию и точку только если timestamp попадает в текущее окно данных
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

                // Горизонтальная линия входа
                double entryZScore = pairData.getZScoreEntry();
                List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
                List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

                XYSeries entryHorizontalLine = chart.addSeries("Entry Z-Score", horizontalLineX, horizontalLineY);
                entryHorizontalLine.setLineColor(Color.BLUE);
                entryHorizontalLine.setMarker(new None());
                entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

                log.info("✅ Линия входа добавлена на графике в позиции {}", index);
            }
        } else if (entryTimestamp > 0) {
            log.warn("⚠️ Время входа не попадает в диапазон истории - показываем приблизительную линию");

            // Показываем линию входа на ближайшей границе
            Date entryDate;
            int index;

            if (entryTimestamp < historyStart) {
                // Время входа раньше истории - показываем в начале
                entryDate = new Date(historyStart);
                index = 0;
                log.info("Показываем линию входа в начале графика");
            } else {
                // Время входа позже истории - показываем в конце
                entryDate = new Date(historyEnd);
                index = timestamps.size() - 1;
                log.info("Показываем линию входа в конце графика");
            }

            List<Date> lineX = Arrays.asList(entryDate, entryDate);
            double minY = zScores.stream().min(Double::compareTo).orElse(-2.0);
            double maxY = zScores.stream().max(Double::compareTo).orElse(2.0);
            List<Double> lineY = Arrays.asList(minY, maxY);

            XYSeries entryLine = chart.addSeries("Entry (approx)", lineX, lineY);
            entryLine.setLineColor(Color.ORANGE);
            entryLine.setMarker(new None());
            entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

            // Горизонтальная линия входа (приблизительная)
            double entryZScore = pairData.getZScoreEntry();
            List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
            List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

            XYSeries entryHorizontalLine = chart.addSeries("Entry Z-Score (approx)", horizontalLineX, horizontalLineY);
            entryHorizontalLine.setLineColor(Color.ORANGE);
            entryHorizontalLine.setMarker(new None());
            entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

            log.info("✅ Приблизительная линия входа добавлена на графике");
        } else {
            log.warn("⚠️ Время входа не задано (0) - линия входа не будет показана");
        }

        return chart;
    }

    private static void addHorizontalLine(XYChart chart, List<Date> timeAxis, double yValue, Color color) {
        List<Double> yLine = Arrays.asList(yValue, yValue);
        XYSeries line = chart.addSeries("level_" + yValue, Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1)), yLine);
        line.setLineColor(color);
        line.setMarker(new None());
        line.setLineStyle(new BasicStroke(2.5f));
    }

    private static OptionalInt findClosestIndex(List<Long> timestamps, long targetTime) {
        for (int i = 0; i < timestamps.size(); i++) {
            if (timestamps.get(i) >= targetTime) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Создает базовый XYChart с Z-Score данными без дополнительных индикаторов
     */
    private static XYChart buildBasicZScoreChart(PairData pairData) {
        // Получаем реальную историю Z-Score из PairData
        List<ZScoreParam> history = pairData.getZScoreHistory();

        List<Long> timestamps;
        List<Double> zScores;

        if (history.isEmpty()) {
            // Fallback: если истории нет, создаем минимальную точку с текущими данными
            log.warn("⚠️ История Z-Score пуста для пары {}, создаем минимальные данные", pairData.getPairName());
            timestamps = Collections.singletonList(System.currentTimeMillis());
            zScores = Collections.singletonList(pairData.getZScoreCurrent());
        } else {
            // Используем реальную историю
            timestamps = history.stream()
                    .map(ZScoreParam::getTimestamp)
                    .collect(Collectors.toList());
            zScores = history.stream()
                    .map(ZScoreParam::getZscore)
                    .collect(Collectors.toList());

            log.info("Используем реальную историю Z-Score: {} точек для пары {}", history.size(), pairData.getPairName());
        }

        log.info("Временной диапазон графика от: {} - до: {}",
                new Date(timestamps.get(0)), new Date(timestamps.get(timestamps.size() - 1)));
        log.info("Текущий Z-Score: {}", pairData.getZScoreCurrent());

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

        // Горизонтальные уровни
        addHorizontalLine(chart, timeAxis, 3.0, Color.BLUE);
        addHorizontalLine(chart, timeAxis, 2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, 1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, 0.0, Color.BLACK);
        addHorizontalLine(chart, timeAxis, -1.0, Color.GRAY);
        addHorizontalLine(chart, timeAxis, -2.0, Color.RED);
        addHorizontalLine(chart, timeAxis, -3.0, Color.BLUE);

        // Вертикальная линия входа - используем entryTime (время создания трейда)
        long entryTimestamp = pairData.getEntryTime() > 0 ? pairData.getEntryTime() : pairData.getTimestamp();
        long historyStart = timestamps.get(0);
        long historyEnd = timestamps.get(timestamps.size() - 1);

        log.info("Проверка линии входа: entryTime={}, historyStart={}, historyEnd={}",
                new Date(entryTimestamp), new Date(historyStart), new Date(historyEnd));
        log.info("PairData: entryTime={}, timestamp={}", pairData.getEntryTime(), pairData.getTimestamp());

        // Проверяем попадает ли время входа в диапазон истории
        boolean inRange = entryTimestamp > 0 && entryTimestamp >= historyStart && entryTimestamp <= historyEnd;

        if (inRange) {
            log.info("Время входа попадает в диапазон истории - рисуем точную линию входа");

            OptionalInt indexOpt = findClosestIndex(timestamps, entryTimestamp);

            // Рисуем линию и точку только если timestamp попадает в текущее окно данных
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

                // Горизонтальная линия входа
                double entryZScore = pairData.getZScoreEntry();
                List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
                List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

                XYSeries entryHorizontalLine = chart.addSeries("Entry Z-Score", horizontalLineX, horizontalLineY);
                entryHorizontalLine.setLineColor(Color.BLUE);
                entryHorizontalLine.setMarker(new None());
                entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

                log.info("✅ Линия входа добавлена на графике в позиции {}", index);
            }
        } else if (entryTimestamp > 0) {
            log.warn("⚠️ Время входа не попадает в диапазон истории - показываем приблизительную линию");

            // Показываем линию входа на ближайшей границе
            Date entryDate;
            int index;

            if (entryTimestamp < historyStart) {
                // Время входа раньше истории - показываем в начале
                entryDate = new Date(historyStart);
                index = 0;
                log.info("Показываем линию входа в начале графика");
            } else {
                // Время входа позже истории - показываем в конце
                entryDate = new Date(historyEnd);
                index = timestamps.size() - 1;
                log.info("Показываем линию входа в конце графика");
            }

            List<Date> lineX = Arrays.asList(entryDate, entryDate);
            double minY = zScores.stream().min(Double::compareTo).orElse(-2.0);
            double maxY = zScores.stream().max(Double::compareTo).orElse(2.0);
            List<Double> lineY = Arrays.asList(minY, maxY);

            XYSeries entryLine = chart.addSeries("Entry (approx)", lineX, lineY);
            entryLine.setLineColor(Color.ORANGE);
            entryLine.setMarker(new None());
            entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

            // Горизонтальная линия входа (приблизительная)
            double entryZScore = pairData.getZScoreEntry();
            List<Date> horizontalLineX = Arrays.asList(timeAxis.get(0), timeAxis.get(timeAxis.size() - 1));
            List<Double> horizontalLineY = Arrays.asList(entryZScore, entryZScore);

            XYSeries entryHorizontalLine = chart.addSeries("Entry Z-Score (approx)", horizontalLineX, horizontalLineY);
            entryHorizontalLine.setLineColor(Color.ORANGE);
            entryHorizontalLine.setMarker(new None());
            entryHorizontalLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6f, 4f}, 0));

            log.info("✅ Приблизительная линия входа добавлена на графике");
        } else {
            log.warn("⚠️ Время входа не задано (0) - линия входа не будет показана");
        }

        return chart;
    }

    /**
     * Создает расширенный XYChart с Z-Score данными и дополнительными индикаторами
     */
    private static XYChart buildEnhancedZScoreChart(PairData pairData, boolean showEma, int emaPeriod, boolean showStochRsi, boolean showProfit) {
        // Сначала создаем базовый чарт без профита
        XYChart chart = buildBasicZScoreChart(pairData);

        // Получаем историю Z-Score для расчета индикаторов
        List<ZScoreParam> history = pairData.getZScoreHistory();

        if (history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста - невозможно рассчитать индикаторы");
            return chart;
        }

        List<Long> timestamps = history.stream()
                .map(ZScoreParam::getTimestamp)
                .collect(Collectors.toList());
        List<Double> zScores = history.stream()
                .map(ZScoreParam::getZscore)
                .collect(Collectors.toList());
        List<Date> timeAxis = timestamps.stream().map(Date::new).collect(Collectors.toList());

        // Добавляем EMA если требуется
        if (showEma && zScores.size() >= emaPeriod) {
            addEmaToChart(chart, timeAxis, zScores, emaPeriod);
        }

        // Добавляем StochRSI если требуется
        if (showStochRsi && zScores.size() >= 14) {
            addStochRsiToChart(chart, timeAxis, zScores);
        }

        // Добавляем график профита если требуется
        if (showProfit) {
            addProfitToChart(chart, pairData);
        }

        return chart;
    }

    /**
     * Добавляет EMA линию на чарт
     */
    private static void addEmaToChart(XYChart chart, List<Date> timeAxis, List<Double> zScores, int period) {
        List<Double> emaValues = calculateEMA(zScores, period);

        // Создаем временную ось для EMA (начинается с позиции period-1)
        List<Date> emaTimeAxis = timeAxis.subList(period - 1, timeAxis.size());

        log.info("Добавляем EMA({}) линию: {} точек", period, emaValues.size());

        XYSeries emaSeries = chart.addSeries("EMA(" + period + ")", emaTimeAxis, emaValues);
        emaSeries.setLineColor(Color.CYAN);
        emaSeries.setMarker(new None());
        emaSeries.setLineStyle(new BasicStroke(2.0f));
    }

    /**
     * Рассчитывает Exponential Moving Average (EMA)
     */
    private static List<Double> calculateEMA(List<Double> values, int period) {
        if (values.size() < period) {
            return new ArrayList<>();
        }

        List<Double> emaValues = new ArrayList<>();
        double multiplier = 2.0 / (period + 1);

        // Первое значение EMA - это простое среднее за период
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += values.get(i);
        }
        double firstEma = sum / period;
        emaValues.add(firstEma);

        // Рассчитываем остальные значения EMA
        for (int i = period; i < values.size(); i++) {
            double currentValue = values.get(i);
            double previousEma = emaValues.get(emaValues.size() - 1);
            double ema = (currentValue * multiplier) + (previousEma * (1 - multiplier));
            emaValues.add(ema);
        }

        return emaValues;
    }

    /**
     * Добавляет StochRSI на чарт (отображается в диапазоне 0-100, масштабированном к текущим значениям Z-Score)
     */
    private static void addStochRsiToChart(XYChart chart, List<Date> timeAxis, List<Double> zScores) {
        List<Double> stochRsiValues = calculateStochRSI(zScores, 14, 3, 3);

        if (stochRsiValues.isEmpty()) {
            log.warn("⚠️ Не удалось рассчитать StochRSI - недостаточно данных");
            return;
        }

        // Создаем временную ось для StochRSI (начинается с позиции 13)
        List<Date> stochRsiTimeAxis = timeAxis.subList(timeAxis.size() - stochRsiValues.size(), timeAxis.size());

        // Масштабируем значения StochRSI к диапазону Z-Score для отображения
        double minZScore = zScores.stream().min(Double::compareTo).orElse(-3.0);
        double maxZScore = zScores.stream().max(Double::compareTo).orElse(3.0);
        double range = maxZScore - minZScore;

        List<Double> scaledStochRsi = stochRsiValues.stream()
                .map(value -> minZScore + (value / 100.0) * range)
                .collect(Collectors.toList());

        log.info("Добавляем StochRSI линию: {} точек (масштабированы в диапазон {}-{})",
                stochRsiValues.size(), minZScore, maxZScore);

        // Основная линия StochRSI
        XYSeries stochRsiSeries = chart.addSeries("StochRSI", stochRsiTimeAxis, scaledStochRsi);
        stochRsiSeries.setLineColor(Color.ORANGE);
        stochRsiSeries.setMarker(new None());
        stochRsiSeries.setLineStyle(new BasicStroke(1.5f));

        // Добавляем уровни перекупленности и перепроданности (80 и 20)
        double overboughtLevel = minZScore + (80.0 / 100.0) * range; // 80%
        double oversoldLevel = minZScore + (20.0 / 100.0) * range;   // 20%

        addHorizontalLine(chart, timeAxis, overboughtLevel, Color.RED);
        addHorizontalLine(chart, timeAxis, oversoldLevel, Color.GREEN);
    }

    /**
     * Рассчитывает Stochastic RSI
     *
     * @param values      исходные значения
     * @param rsiPeriod   период для RSI (обычно 14)
     * @param stochPeriod период для Stochastic (обычно 14)
     * @param smoothK     период сглаживания %K (обычно 3)
     * @return список значений StochRSI (0-100)
     */
    private static List<Double> calculateStochRSI(List<Double> values, int rsiPeriod, int stochPeriod, int smoothK) {
        if (values.size() < rsiPeriod + stochPeriod + smoothK) {
            return new ArrayList<>();
        }

        // Шаг 1: Рассчитываем RSI
        List<Double> rsiValues = calculateRSI(values, rsiPeriod);

        if (rsiValues.size() < stochPeriod) {
            return new ArrayList<>();
        }

        // Шаг 2: Рассчитываем Stochastic для RSI
        List<Double> stochRsiRaw = new ArrayList<>();

        for (int i = stochPeriod - 1; i < rsiValues.size(); i++) {
            List<Double> rsiPeriodValues = rsiValues.subList(i - stochPeriod + 1, i + 1);
            double minRsi = rsiPeriodValues.stream().min(Double::compareTo).orElse(0.0);
            double maxRsi = rsiPeriodValues.stream().max(Double::compareTo).orElse(100.0);
            double currentRsi = rsiValues.get(i);

            double stochRsi;
            if (maxRsi - minRsi == 0) {
                stochRsi = 50.0; // Нейтральное значение
            } else {
                stochRsi = ((currentRsi - minRsi) / (maxRsi - minRsi)) * 100.0;
            }

            stochRsiRaw.add(stochRsi);
        }

        // Шаг 3: Сглаживаем %K (простое скользящее среднее)
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

    /**
     * Рассчитывает RSI (Relative Strength Index)
     */
    private static List<Double> calculateRSI(List<Double> values, int period) {
        if (values.size() < period + 1) {
            return new ArrayList<>();
        }

        List<Double> rsiValues = new ArrayList<>();
        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        // Рассчитываем прибыли и убытки
        for (int i = 1; i < values.size(); i++) {
            double change = values.get(i) - values.get(i - 1);
            gains.add(Math.max(change, 0));
            losses.add(Math.max(-change, 0));
        }

        // Первое значение RSI с простым средним
        double avgGain = gains.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgLoss = losses.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));
        rsiValues.add(rsi);

        // Последующие значения RSI с экспоненциальным сглаживанием
        for (int i = period; i < gains.size(); i++) {
            avgGain = (avgGain * (period - 1) + gains.get(i)) / period;
            avgLoss = (avgLoss * (period - 1) + losses.get(i)) / period;

            rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            rsi = 100 - (100 / (1 + rs));
            rsiValues.add(rsi);
        }

        return rsiValues;
    }

    /**
     * Добавляет график профита на чарт с момента входа в позицию
     */
    private static void addProfitToChart(XYChart chart, PairData pairData) {
        List<ProfitHistoryItem> profitHistory = pairData.getProfitHistory();
        if (profitHistory == null || profitHistory.isEmpty()) {
            log.warn("📊 История профита пуста для пары {}, график профита не будет добавлен.", pairData.getPairName());
            return;
        }

        // Отладочная информация о данных профита
        long entryTimestamp = pairData.getEntryTime() > 0 ? pairData.getEntryTime() : pairData.getTimestamp();
        log.info("📊 Анализ данных профита для пары {}:", pairData.getPairName());
        log.info("📊 Entry timestamp: {} ({})", entryTimestamp, new Date(entryTimestamp));
        log.info("📊 Всего данных профита: {}", profitHistory.size());

        // Показываем первые и последние элементы профита
        if (!profitHistory.isEmpty()) {
            ProfitHistoryItem first = profitHistory.get(0);
            ProfitHistoryItem last = profitHistory.get(profitHistory.size() - 1);
            log.info("📊 Первая запись профита: {} ({})", first.getTimestamp(), new Date(first.getTimestamp()));
            log.info("📊 Последняя запись профита: {} ({})", last.getTimestamp(), new Date(last.getTimestamp()));
        }

        // Фильтруем данные профита с момента входа
        List<ProfitHistoryItem> filteredProfitHistory = profitHistory.stream()
                .filter(item -> item.getTimestamp() >= entryTimestamp)
                .collect(Collectors.toList());

        if (filteredProfitHistory.isEmpty()) {
            log.warn("📊 Нет данных профита с момента входа для пары {}", pairData.getPairName());
            log.warn("📊 Entry time: {}, но все данные профита старше этого времени", new Date(entryTimestamp));

            // В качестве fallback показываем все данные профита
            log.info("📊 Fallback: показываем все данные профита ({} точек)", profitHistory.size());
            filteredProfitHistory = profitHistory;
        }

        log.info("📊 Добавляем на график историю профита: {} точек из {} общих",
                filteredProfitHistory.size(), profitHistory.size());

        List<Date> profitTimeAxis = filteredProfitHistory.stream()
                .map(p -> new Date(p.getTimestamp()))
                .collect(Collectors.toList());
        List<Double> profitValues = filteredProfitHistory.stream()
                .map(ProfitHistoryItem::getProfitPercent)
                .collect(Collectors.toList());

        log.info("📊 Диапазон значений профита: от {} до {}",
                profitValues.stream().min(Double::compareTo).orElse(0.0),
                profitValues.stream().max(Double::compareTo).orElse(0.0));

        XYSeries profitSeries = chart.addSeries("Profit %", profitTimeAxis, profitValues);
        profitSeries.setYAxisGroup(1);
        profitSeries.setLineColor(Color.GREEN);

        // Для малого количества точек показываем маркеры
        if (profitValues.size() <= 2) {
            log.info("📊 Малое количество точек профита ({}), включаем маркеры", profitValues.size());
            profitSeries.setMarker(SeriesMarkers.CIRCLE);
            profitSeries.setMarkerColor(Color.GREEN.darker());
            profitSeries.setLineStyle(new BasicStroke(3.0f)); // Толще линия
        } else {
            profitSeries.setMarker(new None());
            profitSeries.setLineStyle(new BasicStroke(2.0f));
        }

        chart.setYAxisGroupTitle(1, "Profit %");

        log.info("✅ График профита успешно добавлен на чарт");
    }
}
