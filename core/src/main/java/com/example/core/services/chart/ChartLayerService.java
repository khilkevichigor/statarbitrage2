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
    private final TechnicalIndicatorService technicalIndicatorService;

    /**
     * 💰 Добавляет синхронизированный профит на Z-Score чарт
     */
    public void addSynchronizedProfitToChart(XYChart chart, Pair tradingPair) {
        List<ProfitHistoryItem> profitHistory = tradingPair.getProfitHistory();
        List<ZScoreParam> zScoreHistory = tradingPair.getZScoreHistory();

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

        // Интерполируем профит на точные временные метки Z-Score
        List<Double> interpolatedProfitValues = new ArrayList<>();
        for (Long zTimestamp : zScoreTimestamps) {
            Double profitValue = interpolationService.interpolateProfit(profitHistory, zTimestamp);
            interpolatedProfitValues.add(profitValue != null ? profitValue : 0.0);
        }

        log.info("✅ Добавляем ИДЕАЛЬНО синхронизированный профит: {} точек", interpolatedProfitValues.size());

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
     * 📈 Добавляет синхронизированные цены на Z-Score чарт
     * Для секции Z-Score используется наложение в диапазон Z-Score
     */
    public void addSynchronizedPricesToChart(XYChart chart, Pair tradingPair) {
        addSynchronizedPricesToChart(chart, tradingPair, false);
    }

    /**
     * 📈 Добавляет синхронизированные цены на чарт с выбором типа отображения
     * @param chart чарт для добавления
     * @param tradingPair торговая пара
     * @param useNormalizedDisplay true - нормализованное отображение для секции цен, false - наложение в диапазон Z-Score
     */
    public void addSynchronizedPricesToChart(XYChart chart, Pair tradingPair, boolean useNormalizedDisplay) {
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
     * 🟣 Добавляет синхронизированный пиксельный спред на Z-Score чарт
     */
    public void addSynchronizedPixelSpreadToChart(XYChart chart, Pair tradingPair) {
        List<PixelSpreadHistoryItem> pixelHistory = tradingPair.getPixelSpreadHistory();
        List<ZScoreParam> zScoreHistory = tradingPair.getZScoreHistory();

        if (pixelHistory == null || pixelHistory.isEmpty()) {
            log.warn("📊 История пиксельного спреда пуста для пары {}, не можем добавить на Z-Score чарт",
                    tradingPair.getPairName());
            return;
        }

        if (zScoreHistory == null || zScoreHistory.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста - невозможно синхронизировать пиксельный спред");
            return;
        }

        // Сортируем по времени
        pixelHistory.sort(Comparator.comparing(PixelSpreadHistoryItem::getTimestamp));

        // Используем точные временные метки Z-Score
        List<Long> zScoreTimestamps = zScoreHistory.stream()
                .map(ZScoreParam::getTimestamp)
                .toList();
        List<Date> zScoreTimeAxis = zScoreTimestamps.stream().map(Date::new).toList();

        log.info("🎯 Синхронизируем пиксельный спред строго по Z-Score таймштампам: {} точек",
                zScoreTimestamps.size());

        // Интерполируем пиксельные расстояния на точные временные метки Z-Score
        List<Double> interpolatedPixelDistances = new ArrayList<>();
        for (Long zTimestamp : zScoreTimestamps) {
            Double pixelDistance = interpolationService.interpolatePixelSpread(pixelHistory, zTimestamp);
            interpolatedPixelDistances.add(pixelDistance != null ? pixelDistance : 0.0);
        }

        // Нормализация в диапазон Z-Score
        double minZScore = zScoreHistory.stream().mapToDouble(ZScoreParam::getZscore).min().orElse(-3.0);
        double maxZScore = zScoreHistory.stream().mapToDouble(ZScoreParam::getZscore).max().orElse(3.0);

        List<Double> scaledPixelSpread = ChartUtils.normalizeValues(interpolatedPixelDistances, minZScore, maxZScore);

        log.info("✅ Добавляем ИДЕАЛЬНО синхронизированный пиксельный спред: {} точек", scaledPixelSpread.size());

        // Добавляем пиксельный спред
        XYSeries pixelSpreadSeries = chart.addSeries("Pixel Spread (sync)", zScoreTimeAxis, scaledPixelSpread);
        pixelSpreadSeries.setLineColor(ChartUtils.PIXEL_SPREAD_COLOR);
        pixelSpreadSeries.setMarker(new None());
        pixelSpreadSeries.setLineStyle(new BasicStroke(2.0f));

        log.debug("🎯 ИДЕАЛЬНО синхронизированный пиксельный спред добавлен!");
    }

    /**
     * 📈 Добавляет EMA индикатор на чарт
     */
    public void addEmaToChart(XYChart chart, List<Date> timeAxis, List<Double> zScores, int period) {
        List<Double> emaValues = technicalIndicatorService.calculateEMA(zScores, period);

        if (emaValues.isEmpty()) {
            log.warn("⚠️ Не удалось рассчитать EMA({}) - недостаточно данных", period);
            return;
        }

        // Используем точные Z-Score таймштампы
        int emaStartIndex = period - 1;
        List<Date> synchronizedEmaTimeAxis = timeAxis.subList(emaStartIndex, timeAxis.size());

        log.info("🎯 Добавляем синхронизированную EMA({}) линию: {} точек (с {} по {})",
                period, emaValues.size(), synchronizedEmaTimeAxis.get(0),
                synchronizedEmaTimeAxis.get(synchronizedEmaTimeAxis.size() - 1));

        XYSeries emaSeries = chart.addSeries("EMA(" + period + ") sync", synchronizedEmaTimeAxis, emaValues);
        emaSeries.setLineColor(ChartUtils.EMA_COLOR);
        emaSeries.setMarker(new None());
        emaSeries.setLineStyle(new BasicStroke(2.0f));
    }

    /**
     * 🌊 Добавляет StochRSI индикатор на чарт
     */
    public void addStochRsiToChart(XYChart chart, List<Date> timeAxis, List<Double> zScores) {
        List<Double> stochRsiValues = technicalIndicatorService.calculateStochRSI(zScores, 14, 3, 3);

        if (stochRsiValues.isEmpty()) {
            log.warn("⚠️ Не удалось рассчитать StochRSI - недостаточно данных");
            return;
        }

        // Используем точные Z-Score таймштампы
        int stochRsiStartIndex = timeAxis.size() - stochRsiValues.size();
        List<Date> synchronizedStochRsiTimeAxis = timeAxis.subList(stochRsiStartIndex, timeAxis.size());

        // Масштабируем в диапазон Z-Score
        double minZScore = zScores.stream().min(Double::compareTo).orElse(-3.0);
        double maxZScore = zScores.stream().max(Double::compareTo).orElse(3.0);
        double range = maxZScore - minZScore;

        List<Double> scaledStochRsi = stochRsiValues.stream()
                .map(value -> minZScore + (value / 100.0) * range)
                .toList();

        log.info("🎯 Добавляем синхронизированную StochRSI линию: {} точек (с {} по {})",
                stochRsiValues.size(), synchronizedStochRsiTimeAxis.get(0),
                synchronizedStochRsiTimeAxis.get(synchronizedStochRsiTimeAxis.size() - 1));

        XYSeries stochRsiSeries = chart.addSeries("StochRSI sync", synchronizedStochRsiTimeAxis, scaledStochRsi);
        stochRsiSeries.setLineColor(ChartUtils.STOCHRSI_COLOR);
        stochRsiSeries.setMarker(new None());
        stochRsiSeries.setLineStyle(new BasicStroke(1.5f));

        // Добавляем горизонтальные линии 80/20 уровней
        double overboughtLevel = minZScore + (80.0 / 100.0) * range;
        double oversoldLevel = minZScore + (20.0 / 100.0) * range;

        ChartUtils.addHorizontalLine(chart, timeAxis, overboughtLevel, Color.RED);
        ChartUtils.addHorizontalLine(chart, timeAxis, oversoldLevel, Color.GREEN);
    }
}