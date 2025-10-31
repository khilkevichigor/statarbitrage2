package com.example.core.services.chart;

import com.example.shared.dto.PixelSpreadHistoryItem;
import com.example.shared.dto.ProfitHistoryItem;
import com.example.shared.dto.ZScoreParam;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.None;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * 🎨 Сервис для добавления дополнительных слоев на чарты
 * Отвечает за добавление цен, профита, пиксельного спреда и технических индикаторов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartLayerService {

    private final InterpolationService interpolationService;

    /**
     * 💰 Добавляет синхронизированный профит на Z-Score чарт
     */
    public void addSynchronizedProfitToChart(XYChart chart, Pair tradingPair) {
        List<ProfitHistoryItem> profitHistory = tradingPair.getProfitHistory();
        List<ZScoreParam> zScoreHistory = tradingPair.getZScoreHistory();

        log.info("🎯 addSynchronizedProfitToChart для пары {}: profitHistory={} точек, zScoreHistory={} точек", 
                tradingPair.getPairName(), 
                profitHistory != null ? profitHistory.size() : "null",
                zScoreHistory != null ? zScoreHistory.size() : "null");

        if (profitHistory != null && !profitHistory.isEmpty()) {
            log.info("📊 Первые несколько точек профита: {}", 
                    profitHistory.stream().limit(3).map(item -> 
                            String.format("%.2f%% в %s", item.getProfitPercent(), new java.util.Date(item.getTimestamp()))
                    ).toList());
        }

        if (profitHistory == null || profitHistory.isEmpty()) {
            log.debug("📊 История профита пуста для пары {}, график профита не будет добавлен.",
                    tradingPair.getPairName());
            return;
        }

        if (zScoreHistory == null || zScoreHistory.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста - невозможно синхронизировать профит");
            addFallbackProfitToChart(chart, profitHistory);
            return;
        }

        // Сортируем по времени
        profitHistory.sort(Comparator.comparing(ProfitHistoryItem::getTimestamp));

        // Получаем временные метки Z-Score для синхронизации
        List<Long> zScoreTimestamps = zScoreHistory.stream()
                .map(ZScoreParam::getTimestamp)
                .toList();
        List<Date> zScoreTimeAxis = zScoreTimestamps.stream().map(Date::new).toList();

        log.info("🎯 Синхронизируем профит строго по Z-Score таймштампам: {} точек", zScoreTimestamps.size());

        // Интерполируем профит на точные временные метки Z-Score с carry-forward стратегией
        List<Double> interpolatedProfitValues = new ArrayList<>();
        Double lastKnownValue = 0.0; // Начинаем с 0 до первой записи
        
        // Находим время первой записи профита для определения стратегии
        long firstProfitTimestamp = profitHistory.get(0).getTimestamp();
        
        for (Long zTimestamp : zScoreTimestamps) {
            Double profitValue = interpolationService.interpolateProfit(profitHistory, zTimestamp);
            
            if (profitValue != null) {
                // Есть данные - используем их
                lastKnownValue = profitValue;
                interpolatedProfitValues.add(profitValue);
            } else {
                // Нет данных - применяем стратегию
                if (zTimestamp < firstProfitTimestamp) {
                    // До первой записи профита - используем 0
                    interpolatedProfitValues.add(0.0);
                } else {
                    // После первой записи - используем последнее известное значение (carry forward)
                    interpolatedProfitValues.add(lastKnownValue);
                }
            }
        }

        log.info("✅ Добавляем ИДЕАЛЬНО синхронизированный профит: {} точек", interpolatedProfitValues.size());
        
        // Логируем статистику интерполированных значений
        long nonZeroCount = interpolatedProfitValues.stream().mapToLong(v -> v != 0.0 ? 1 : 0).sum();
        double minValue = interpolatedProfitValues.stream().mapToDouble(v -> v).min().orElse(0.0);
        double maxValue = interpolatedProfitValues.stream().mapToDouble(v -> v).max().orElse(0.0);
        
        log.info("📈 Статистика интерполированного профита: {} ненулевых из {}, диапазон [{:.2f}% - {:.2f}%]",
                nonZeroCount, interpolatedProfitValues.size(), minValue, maxValue);

        addProfitSeriesToChart(chart, zScoreTimeAxis, interpolatedProfitValues);
    }

    /**
     * 🔄 Fallback метод для добавления профита без синхронизации
     */
    private void addFallbackProfitToChart(XYChart chart, List<ProfitHistoryItem> profitHistory) {
        List<Date> profitTimeAxis = profitHistory.stream()
                .map(p -> new Date(p.getTimestamp()))
                .toList();
        List<Double> profitValues = profitHistory.stream()
                .map(ProfitHistoryItem::getProfitPercent)
                .toList();

        addProfitSeriesToChart(chart, profitTimeAxis, profitValues);
    }

    /**
     * 📊 Добавляет серию профита на чарт
     */
    private void addProfitSeriesToChart(XYChart chart, List<Date> timeAxis, List<Double> profitValues) {
        if (!ChartUtils.isValidChartData(timeAxis, profitValues)) {
            log.warn("⚠️ Невалидные данные профита для добавления на чарт");
            return;
        }

        XYSeries profitSeries = chart.addSeries("Profit % (sync)", timeAxis, profitValues);
        profitSeries.setYAxisGroup(1);
        profitSeries.setLineColor(ChartUtils.PROFIT_COLOR);
        profitSeries.setMarker(new None());
        profitSeries.setLineStyle(new BasicStroke(2.0f));

        // Последняя точка профита
        if (!timeAxis.isEmpty() && !profitValues.isEmpty()) {
            Date lastTime = timeAxis.get(timeAxis.size() - 1);
            Double lastValue = profitValues.get(profitValues.size() - 1);

            XYSeries lastPointSeries = chart.addSeries("Last Profit Point (sync)",
                    Collections.singletonList(lastTime),
                    Collections.singletonList(lastValue));
            lastPointSeries.setYAxisGroup(1);
            lastPointSeries.setMarker(SeriesMarkers.CIRCLE);
            lastPointSeries.setMarkerColor(Color.RED);
        }

        chart.setYAxisGroupTitle(1, "Profit %");
        log.debug("🎯 ИДЕАЛЬНО синхронизированный профит добавлен на чарт!");
    }

    /**
     * 📈 Добавляет синхронизированные цены на чарт с выбором типа отображения и точкой входа
     * @param chart чарт для добавления
     * @param tradingPair торговая пара
     * @param useNormalizedDisplay true - нормализованное отображение для секции цен, false - наложение в диапазон Z-Score
     * @param showEntryPoint true - добавить точку входа после данных
     */
    public void addSynchronizedPricesToChart(XYChart chart, Pair tradingPair, boolean useNormalizedDisplay, boolean showEntryPoint) {
        String longTicker = tradingPair.getLongTicker();
        String shortTicker = tradingPair.getShortTicker();

        var longCandles = tradingPair.getLongTickerCandles();
        var shortCandles = tradingPair.getShortTickerCandles();
        List<ZScoreParam> history = tradingPair.getZScoreHistory();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Не найдены данные для синхронизированных цен на чарт: longCandles={}, shortCandles={}",
                    ChartUtils.safeListSize(longCandles), ChartUtils.safeListSize(shortCandles));
            return;
        }

        if (history == null || history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста - невозможно синхронизировать цены");
            return;
        }

        // Сортировка по времени
        longCandles.sort(Comparator.comparing(candle -> candle.getTimestamp()));
        shortCandles.sort(Comparator.comparing(candle -> candle.getTimestamp()));

        // Используем точные временные метки Z-Score как основу
        List<Long> zScoreTimestamps = history.stream()
                .map(ZScoreParam::getTimestamp)
                .toList();
        List<Date> zScoreTimeAxis = zScoreTimestamps.stream().map(Date::new).toList();

        log.info("🎯 Синхронизируем цены строго по Z-Score таймштампам: {} точек", zScoreTimestamps.size());

        // Интерполируем цены на точные временные метки Z-Score
        List<Double> interpolatedLongPrices = interpolatePricesForTimestamps(longCandles, zScoreTimestamps);
        List<Double> interpolatedShortPrices = interpolatePricesForTimestamps(shortCandles, zScoreTimestamps);

        List<Double> finalLongPrices;
        List<Double> finalShortPrices;
        String displayMode;

        if (useNormalizedDisplay) {
            // Нормализованное отображение для секции цен - приводим к процентам изменения
            finalLongPrices = normalizeToPercentageChanges(interpolatedLongPrices);
            finalShortPrices = normalizeToPercentageChanges(interpolatedShortPrices);
            displayMode = "normalized %";
        } else {
            // Классическое наложение в диапазон Z-Score для секции Z-Score
            double minZScore = history.stream().mapToDouble(ZScoreParam::getZscore).min().orElse(-3.0);
            double maxZScore = history.stream().mapToDouble(ZScoreParam::getZscore).max().orElse(3.0);

            finalLongPrices = ChartUtils.normalizeValues(interpolatedLongPrices, minZScore, maxZScore);
            finalShortPrices = ChartUtils.normalizeValues(interpolatedShortPrices, minZScore, maxZScore);
            displayMode = "z-score overlay";
        }

        log.info("✅ Добавляем ИДЕАЛЬНО синхронизированные цены ({}): {} точек", 
                displayMode, finalLongPrices.size());

        // Выбираем цвета в зависимости от режима отображения
        Color longColor = useNormalizedDisplay ? ChartUtils.LONG_PRICE_NORMALIZED_COLOR : ChartUtils.LONG_PRICE_COLOR;
        Color shortColor = useNormalizedDisplay ? ChartUtils.SHORT_PRICE_NORMALIZED_COLOR : ChartUtils.SHORT_PRICE_COLOR;

        // Добавляем линии цен
        addPriceSeries(chart, "LONG " + longTicker + " (" + displayMode + ")", zScoreTimeAxis, finalLongPrices, longColor);
        addPriceSeries(chart, "SHORT " + shortTicker + " (" + displayMode + ")", zScoreTimeAxis, finalShortPrices, shortColor);

        log.debug("🎯 ИДЕАЛЬНО синхронизированные цены добавлены (режим: {})!", displayMode);
        
        // Добавляем точку входа если требуется и это нормализованный режим
        if (showEntryPoint && useNormalizedDisplay) {
            addEntryPointToNormalizedChart(chart, tradingPair, zScoreTimeAxis, finalLongPrices, finalShortPrices);
        }
    }

    /**
     * 📊 Нормализует цены к процентным изменениям от первой точки
     * Сохраняет точное поведение цен без наложения графиков
     */
    private List<Double> normalizeToPercentageChanges(List<Double> prices) {
        if (prices.isEmpty()) {
            log.warn("⚠️ Пустой список цен для нормализации");
            return prices;
        }

        double basePrice = prices.get(0);
        if (basePrice == 0) {
            log.warn("⚠️ Нулевая базовая цена - возвращаем исходные данные");
            return prices;
        }

        List<Double> normalizedPrices = prices.stream()
                .map(price -> ((price - basePrice) / basePrice) * 100.0)
                .toList();

        log.debug("📊 Нормализация цен: база={}, диапазон изменений [{}, {}]%",
                basePrice,
                normalizedPrices.stream().min(Double::compareTo).orElse(0.0),
                normalizedPrices.stream().max(Double::compareTo).orElse(0.0));

        return normalizedPrices;
    }

    /**
     * 🔄 Интерполирует цены для списка таймштампов
     */
    private List<Double> interpolatePricesForTimestamps(List<?> candles, List<Long> timestamps) {
        List<Double> result = new ArrayList<>();
        for (Long timestamp : timestamps) {
            Double price = interpolationService.interpolatePrice((List) candles, timestamp);
            result.add(price != null ? price : 0.0);
        }
        return result;
    }

    /**
     * 📊 Добавляет серию цен на чарт
     */
    private void addPriceSeries(XYChart chart, String seriesName, List<Date> timeAxis,
                                List<Double> prices, Color color) {
        XYSeries priceSeries = chart.addSeries(seriesName, timeAxis, prices);
        priceSeries.setLineColor(color);
        priceSeries.setMarker(new None());
        
        // Для нормализованного режима используем более толстые линии
        boolean isNormalizedMode = seriesName.contains("normalized %");
        float lineWidth = isNormalizedMode ? 2.5f : 1.5f;
        priceSeries.setLineStyle(new BasicStroke(lineWidth));
    }

    /**
     * 🎯 Добавляет точку входа на нормализованный чарт цен с правильным масштабированием
     */
    private void addEntryPointToNormalizedChart(XYChart chart, Pair tradingPair, List<Date> timeAxis, 
                                                List<Double> longPrices, List<Double> shortPrices) {
        long entryTimestamp = getEntryTimestamp(tradingPair);

        if (entryTimestamp <= 0) {
            log.debug("⚠️ Время входа не задано (0) - линия входа не будет показана");
            return;
        }

        long historyStart = timeAxis.get(0).getTime();
        long historyEnd = timeAxis.get(timeAxis.size() - 1).getTime();

        log.debug("🔍 Проверка линии входа для нормализованного чарта: entryTime={}, historyStart={}, historyEnd={}",
                new Date(entryTimestamp), new Date(historyStart), new Date(historyEnd));

        Date entryDate;
        String seriesName;
        Color color;

        boolean inRange = entryTimestamp >= historyStart && entryTimestamp <= historyEnd;

        if (inRange) {
            entryDate = new Date(entryTimestamp);
            seriesName = "Entry";
            color = ChartUtils.ENTRY_POINT_COLOR;
            log.debug("🎯 Время входа попадает в диапазон истории - рисуем точную линию входа");
        } else {
            if (entryTimestamp < historyStart) {
                entryDate = new Date(historyStart);
                log.debug("📍 Показываем линию входа в начале графика");
            } else {
                entryDate = new Date(historyEnd);
                log.debug("📍 Показываем линию входа в конце графика");
            }
            seriesName = "Entry (approx)";
            color = ChartUtils.ENTRY_POINT_APPROX_COLOR;
        }

        // Вычисляем реальный диапазон нормализованных данных
        List<Double> allPrices = new ArrayList<>();
        allPrices.addAll(longPrices);
        allPrices.addAll(shortPrices);
        
        double minPrice = allPrices.stream().min(Double::compareTo).orElse(-10.0);
        double maxPrice = allPrices.stream().max(Double::compareTo).orElse(10.0);
        
        // Добавляем небольшой отступ для лучшей видимости
        double padding = (maxPrice - minPrice) * 0.05;
        double lineMinY = minPrice - padding;
        double lineMaxY = maxPrice + padding;

        // Добавляем вертикальную линию входа с правильным диапазоном
        List<Date> lineX = Arrays.asList(entryDate, entryDate);
        List<Double> lineY = Arrays.asList(lineMinY, lineMaxY);

        XYSeries entryLine = chart.addSeries(seriesName, lineX, lineY);
        entryLine.setLineColor(color);
        entryLine.setMarker(new None());
        entryLine.setLineStyle(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                0, new float[]{6f, 4f}, 0));

        log.debug("✅ Вертикальная линия входа добавлена на нормализованный чарт (диапазон: {} - {})", 
                lineMinY, lineMaxY);
    }

    /**
     * 🕐 Получает таймштамп входа из данных торговой пары
     */
    private long getEntryTimestamp(Pair tradingPair) {
        if (tradingPair.getEntryTime() != null) {
            return tradingPair.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (tradingPair.getTimestamp() != null) {
            return tradingPair.getTimestamp();
        }
        return System.currentTimeMillis();
    }
}