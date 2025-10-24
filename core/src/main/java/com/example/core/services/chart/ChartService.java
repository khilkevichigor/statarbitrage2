package com.example.core.services.chart;

import com.example.core.services.PixelSpreadService;
import com.example.shared.dto.ZScoreParam;
import com.example.shared.models.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 🎨 Рефакторенный сервис для создания графиков
 * Использует компонентный подход с разделением ответственности
 * <p>
 * Архитектура:
 * - ChartUtils: Общие утилиты и константы
 * - InterpolationService: Интерполяция данных для синхронизации
 * - TechnicalIndicatorService: Расчет технических индикаторов
 * - ZScoreChartBuilder: Построение базовых Z-Score чартов
 * - ChartLayerService: Добавление дополнительных слоев
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartService {

    private final ZScoreChartBuilder zScoreChartBuilder;
    private final ChartLayerService chartLayerService;
    private final InterpolationService interpolationService;
    private final PixelSpreadService pixelSpreadService;
    private final VerticalChartBuilder verticalChartBuilder;

    /**
     * 🎯 Создает расширенный Z-Score график с дополнительными индикаторами и слоями
     *
     * @param tradingPair       Торговая пара
     * @param showEma           Показать EMA индикатор
     * @param emaPeriod         Период EMA
     * @param showStochRsi      Показать StochRSI индикатор
     * @param showProfit        Показать график профита
     * @param showCombinedPrice Показать наложенные цены
     * @param showPixelSpread   Показать пиксельный спред
     * @param showEntryPoint    Показать точку входа
     * @return Готовое изображение графика
     */
    public BufferedImage createZScoreChart(Pair tradingPair, boolean showEma, int emaPeriod,
                                           boolean showStochRsi, boolean showProfit,
                                           boolean showCombinedPrice, boolean showPixelSpread,
                                           boolean showEntryPoint) {

        log.debug("🎨 Создание расширенного Z-Score графика для пары: {} " +
                        "(EMA: {}, период: {}, StochRSI: {}, Profit: {}, CombinedPrice: {}, PixelSpread: {}, EntryPoint: {})",
                tradingPair.getPairName(), showEma, emaPeriod, showStochRsi, showProfit,
                showCombinedPrice, showPixelSpread, showEntryPoint);

        XYChart chart = buildEnhancedZScoreChart(tradingPair, showEma, emaPeriod, showStochRsi,
                showProfit, showCombinedPrice, showPixelSpread, showEntryPoint);

        BufferedImage result = BitmapEncoder.getBufferedImage(chart);
        log.debug("✅ Расширенный Z-Score график создан для пары {}", tradingPair.getPairName());

        return result;
    }

    /**
     * 🏗️ Строит расширенный Z-Score график с всеми запрошенными слоями
     */
    private XYChart buildEnhancedZScoreChart(Pair tradingPair, boolean showEma, int emaPeriod,
                                             boolean showStochRsi, boolean showProfit,
                                             boolean showCombinedPrice, boolean showPixelSpread,
                                             boolean showEntryPoint) {

        // Создаем базовый Z-Score график
        XYChart chart = zScoreChartBuilder.buildBasicZScoreChart(tradingPair, showEntryPoint);

        List<ZScoreParam> history = tradingPair.getZScoreHistory();
        if (history.isEmpty()) {
            log.warn("⚠️ История Z-Score пуста - невозможно рассчитать дополнительные индикаторы");
            return chart;
        }

        // Получаем базовые данные для всех дополнительных слоев
        List<Date> timeAxis = history.stream()
                .map(param -> new Date(param.getTimestamp()))
                .toList();
        List<Double> zScores = history.stream()
                .map(ZScoreParam::getZscore)
                .toList();

        log.debug("📊 Базовый график создан, добавляем дополнительные слои...");

        // Добавляем технические индикаторы (они работают с данными Z-Score)
        if (showEma && zScores.size() >= emaPeriod) {
            log.debug("📈 Добавляем EMA({}) индикатор", emaPeriod);
            chartLayerService.addEmaToChart(chart, timeAxis, zScores, emaPeriod);
        }

        if (showStochRsi && zScores.size() >= 14) {
            log.debug("🌊 Добавляем StochRSI индикатор");
            chartLayerService.addStochRsiToChart(chart, timeAxis, zScores);
        }

        // Добавляем слои данных (они интерполируются на Z-Score временные метки)
        if (showProfit) {
            log.debug("💰 Добавляем синхронизированный профит");
            chartLayerService.addSynchronizedProfitToChart(chart, tradingPair);
        }

        if (showCombinedPrice) {
            log.debug("📈 Добавляем синхронизированные цены");
            chartLayerService.addSynchronizedPricesToChart(chart, tradingPair);
        }

        if (showPixelSpread) {
            log.debug("🟣 Добавляем синхронизированный пиксельный спред");
            chartLayerService.addSynchronizedPixelSpreadToChart(chart, tradingPair);
        }

        log.debug("✅ Все дополнительные слои добавлены на график");
        return chart;
    }

    /**
     * 📊 Создает Price чарт синхронизированный с Z-Score периодом
     * Совместимость с существующим API
     */
    public BufferedImage createPriceChart(Pair tradingPair) {
        log.debug("📊 Создание Price графика для пары: {}", tradingPair.getPairName());

        // Используем новую архитектуру для создания чарта только с ценами
        return createZScoreChart(tradingPair, false, 0, false, false, true, false, false);
    }

    /**
     * 📊 Создает Price чарт с профитом
     * Совместимость с существующим API
     */
    public BufferedImage createPriceChartWithProfit(Pair tradingPair) {
        log.debug("📊 Создание Price графика с профитом для пары: {}", tradingPair.getPairName());

        // Используем новую архитектуру для создания чарта с ценами и профитом
        return createZScoreChart(tradingPair, false, 0, false, true, true, false, false);
    }

    /**
     * 📊 Создает синхронизированный Price чарт
     * Совместимость с существующим API
     */
    public BufferedImage createSynchronizedPriceChart(Pair tradingPair) {
        log.debug("📊 Создание синхронизированного Price графика для пары: {}", tradingPair.getPairName());

        // Новая архитектура автоматически обеспечивает синхронизацию
        return createPriceChart(tradingPair);
    }

    /**
     * 📊 Создает комбинированный чарт (Z-Score + цены + пиксельный спред)
     * Совместимость с существующим API
     */
    public BufferedImage createCombinedChart(Pair tradingPair) {
        log.debug("📊 Создание комбинированного графика для пары: {}", tradingPair.getPairName());

        // Используем новую архитектуру для создания полного комбинированного чарта
        return createZScoreChart(tradingPair, false, 0, false, false, true, true, true);
    }

    /**
     * 🟣 Создает Pixel Spread чарт
     * Совместимость с существующим API
     */
    public BufferedImage createPixelSpreadChart(Pair tradingPair) {
        log.debug("🟣 Создание Pixel Spread графика для пары: {}", tradingPair.getPairName());

        // Используем новую архитектуру для создания чарта только с пиксельным спредом
        return createZScoreChart(tradingPair, false, 0, false, false, false, true, false);
    }

    /**
     * 🟣 Создает Pixel Spread чарт с профитом
     * Совместимость с существующим API
     */
    public BufferedImage createPixelSpreadChartWithProfit(Pair tradingPair) {
        log.debug("🟣 Создание Pixel Spread графика с профитом для пары: {}", tradingPair.getPairName());

        // Используем новую архитектуру для создания чарта с пиксельным спредом и профитом
        return createZScoreChart(tradingPair, false, 0, false, true, false, true, false);
    }

    /**
     * 📈 Создает чарт нормализованных пересечений цен
     * Совместимость с существующим API - делегирует старой реализации пока не нужен рефакторинг
     */
    public BufferedImage createNormalizedPriceIntersectionsChart(Pair tradingPair, int intersectionCount) {
        log.debug("📈 Создание графика пересечений для пары: {} (пересечений: {})",
                tradingPair.getPairName(), intersectionCount);

        // Реализация для совместимости с существующим API
        List<com.example.shared.dto.Candle> longCandles = tradingPair.getLongTickerCandles();
        List<com.example.shared.dto.Candle> shortCandles = tradingPair.getShortTickerCandles();

        return createNormalizedPriceIntersectionsChart(longCandles, shortCandles,
                tradingPair.getPairName(), intersectionCount, false);
    }

    /**
     * 🏗️ УПРОЩЕННЫЙ МЕТОД: Создает полный вертикальный чарт со ВСЕМИ секциями
     * 🎯 УПРОЩЕНИЕ: Больше никаких чекбоксов! Все секции отображаются всегда!
     *
     * @param tradingPair    Торговая пара
     * @param showEntryPoint Показать точки входа на всех секциях
     * @return Готовое изображение ПОЛНОГО вертикального чарта
     */
    public BufferedImage createVerticalChart(Pair tradingPair, boolean showEntryPoint) {

        log.debug("🏗️ УПРОЩЕННОЕ создание ПОЛНОГО вертикального чарта для пары: {} (EntryPoint: {})",
                tradingPair.getPairName(), showEntryPoint);

        // Используем новый VerticalChartBuilder с упрощенным API
        BufferedImage result = verticalChartBuilder.createVerticalChart(tradingPair, showEntryPoint);

        log.debug("✅ ПОЛНЫЙ вертикальный чарт создан для пары {}", tradingPair.getPairName());
        return result;
    }

    /**
     * 🔍 Найти ближайшую цену по временной метке
     * Совместимость с существующим API
     */
    public Double findNearestPrice(List<Date> timeAxis, List<Double> prices, long targetTimestamp) {
        return interpolationService.findNearestPrice(timeAxis, prices, targetTimestamp);
    }

    /**
     * 📁 Сохранить чарт в файл (совместимость с существующим API)
     */
    public void saveChartToFile(BufferedImage chartImage, String filename) {
        try {
            // Логика сохранения файла - может быть вынесена в отдельный сервис
            log.debug("💾 Сохранение графика в файл: {}", filename);
            // TODO: Реализовать сохранение если нужно
        } catch (Exception e) {
            log.error("❌ Ошибка при сохранении графика в файл {}: {}", filename, e.getMessage(), e);
        }
    }

    /**
     * 🟣 Рассчитывает пиксельный спред если нужно
     * Делегирует вызов к PixelSpreadService для совместимости API
     */
    public void calculatePixelSpreadIfNeeded(Pair tradingPair) {
        pixelSpreadService.calculatePixelSpreadIfNeeded(tradingPair);
    }

    /**
     * 🟣 Добавляет текущую точку пиксельного спреда
     * Делегирует вызов к PixelSpreadService для совместимости API
     */
    public void addCurrentPixelSpreadPoint(Pair tradingPair) {
        pixelSpreadService.addCurrentPixelSpreadPoint(tradingPair);
    }

    /**
     * 📊 Создает Price чарт с профитом (расширенная версия для совместимости)
     * Совместимость с существующим API
     */
    public BufferedImage createPriceChartWithProfit(Pair tradingPair, boolean showEma, boolean showStochRsi, boolean showEntryPoint) {
        log.debug("📊 Создание Price графика с профитом для пары: {} (EMA: {}, StochRSI: {}, EntryPoint: {})",
                tradingPair.getPairName(), showEma, showStochRsi, showEntryPoint);

        // Используем новую архитектуру с дополнительными параметрами
        return createZScoreChart(tradingPair, showEma, 14, showStochRsi, true, true, false, showEntryPoint);
    }

    /**
     * 🟣 Создает Pixel Spread чарт с профитом (расширенная версия для совместимости)
     * Совместимость с существующим API
     */
    public BufferedImage createPixelSpreadChartWithProfit(Pair tradingPair, boolean showProfit, boolean showEntryPoint) {
        log.debug("🟣 Создание Pixel Spread графика с профитом для пары: {} (Profit: {}, EntryPoint: {})",
                tradingPair.getPairName(), showProfit, showEntryPoint);

        // Используем новую архитектуру
        return createZScoreChart(tradingPair, false, 0, false, showProfit, false, true, showEntryPoint);
    }

    /**
     * 📊 Создает комбинированный чарт (расширенная версия для совместимости)
     * Совместимость с существующим API
     */
    public BufferedImage createCombinedChart(Pair tradingPair, boolean showEma, boolean showStochRsi,
                                             boolean showProfit, boolean showEntryPoint, int emaPeriod,
                                             boolean showCombinedPrice, boolean showPixelSpread, boolean detailed) {
        log.debug("📊 Создание расширенного комбинированного графика для пары: {} " +
                        "(EMA: {}, StochRSI: {}, Profit: {}, Entry: {}, Period: {}, Prices: {}, PixelSpread: {}, Detailed: {})",
                tradingPair.getPairName(), showEma, showStochRsi, showProfit, showEntryPoint,
                emaPeriod, showCombinedPrice, showPixelSpread, detailed);

        // Используем новую архитектуру со всеми параметрами
        return createZScoreChart(tradingPair, showEma, emaPeriod, showStochRsi, showProfit,
                showCombinedPrice, showPixelSpread, showEntryPoint);
    }

    /**
     * 📈 Создает чарт нормализованных пересечений цен (расширенная версия для совместимости)
     * Совместимость с существующим API
     */
    @SuppressWarnings("unchecked")
    public BufferedImage createNormalizedPriceIntersectionsChart(List<?> longCandles, List<?> shortCandles,
                                                                 String pairName, int intersectionCount,
                                                                 boolean showDetails) {
        log.debug("📈 Создание графика пересечений: пара={}, пересечений={}, детали={}",
                pairName, intersectionCount, showDetails);

        try {
            // Приводим к нужному типу
            List<com.example.shared.dto.Candle> longCandlesList = (List<com.example.shared.dto.Candle>) longCandles;
            List<com.example.shared.dto.Candle> shortCandlesList = (List<com.example.shared.dto.Candle>) shortCandles;

            if (longCandlesList.isEmpty() || shortCandlesList.isEmpty()) {
                log.warn("⚠️ Пустые данные свечей для создания графика пересечений");
                return createEmptyChart("Нет данных для графика пересечений: " + pairName);
            }

            int minSize = Math.min(longCandlesList.size(), shortCandlesList.size());
            if (minSize < 2) {
                log.warn("⚠️ Недостаточно данных для создания графика пересечений: {}", minSize);
                return createEmptyChart("Недостаточно данных: " + pairName);
            }

            // Создаем временные оси и нормализованные цены
            List<Date> timeAxis = new ArrayList<>();
            List<Double> longPrices = new ArrayList<>();
            List<Double> shortPrices = new ArrayList<>();

            // Берем данные из свечей
            for (int i = 0; i < minSize; i++) {
                com.example.shared.dto.Candle longCandle = longCandlesList.get(i);
                com.example.shared.dto.Candle shortCandle = shortCandlesList.get(i);

                timeAxis.add(new Date(longCandle.getTimestamp()));
                longPrices.add(longCandle.getClose());
                shortPrices.add(shortCandle.getClose());
            }

            // Нормализуем цены (приводим к диапазону 0-1)
            List<Double> normalizedLongPrices = normalizePrices(longPrices);
            List<Double> normalizedShortPrices = normalizePrices(shortPrices);

            // Создаем график
            XYChart chart = new org.knowm.xchart.XYChartBuilder()
                    .width(ChartUtils.CHART_WIDTH)
                    .height(ChartUtils.CHART_HEIGHT)
                    .title("Нормализованные цены и пересечения: " + pairName)
                    .xAxisTitle("Время")
                    .yAxisTitle("Нормализованная цена")
                    .build();

            ChartUtils.applyUnifiedChartStyle(chart, timeAxis);

            // Добавляем линии цен
            org.knowm.xchart.XYSeries longSeries = chart.addSeries("LONG цена", timeAxis, normalizedLongPrices);
            longSeries.setLineColor(java.awt.Color.BLUE);
            longSeries.setMarker(new org.knowm.xchart.style.markers.None());

            org.knowm.xchart.XYSeries shortSeries = chart.addSeries("SHORT цена", timeAxis, normalizedShortPrices);
            shortSeries.setLineColor(java.awt.Color.RED);
            shortSeries.setMarker(new org.knowm.xchart.style.markers.None());

            // Отмечаем пересечения если нужно
            if (showDetails && intersectionCount > 0) {
                addIntersectionPoints(chart, timeAxis, normalizedLongPrices, normalizedShortPrices);
            }

            BufferedImage result = BitmapEncoder.getBufferedImage(chart);
            log.debug("✅ График пересечений создан для пары {}", pairName);

            return result;

        } catch (Exception e) {
            log.error("❌ Ошибка при создании графика пересечений для пары {}: {}", pairName, e.getMessage(), e);
            return createEmptyChart("Ошибка создания графика: " + pairName);
        }
    }

    /**
     * 🔄 Нормализует список цен к диапазону 0-1
     */
    private List<Double> normalizePrices(List<Double> prices) {
        if (prices.isEmpty()) return prices;

        double min = prices.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = prices.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double range = max - min;

        if (range == 0) {
            // Все цены одинаковые
            return prices.stream().map(p -> 0.5).toList();
        }

        return prices.stream()
                .map(price -> (price - min) / range)
                .toList();
    }

    /**
     * 📍 Добавляет точки пересечений на график
     */
    private void addIntersectionPoints(XYChart chart, List<Date> timeAxis,
                                       List<Double> longPrices, List<Double> shortPrices) {
        List<Date> intersectionTimes = new ArrayList<>();
        List<Double> intersectionValues = new ArrayList<>();

        boolean longAboveShort = longPrices.get(0) > shortPrices.get(0);

        for (int i = 1; i < longPrices.size(); i++) {
            boolean currentLongAboveShort = longPrices.get(i) > shortPrices.get(i);

            if (longAboveShort != currentLongAboveShort) {
                // Найдено пересечение
                intersectionTimes.add(timeAxis.get(i));
                intersectionValues.add((longPrices.get(i) + shortPrices.get(i)) / 2.0);
                longAboveShort = currentLongAboveShort;
            }
        }

        if (!intersectionTimes.isEmpty()) {
            org.knowm.xchart.XYSeries intersectionSeries = chart.addSeries("Пересечения",
                    intersectionTimes, intersectionValues);
            intersectionSeries.setLineColor(java.awt.Color.GREEN);
            intersectionSeries.setMarker(new org.knowm.xchart.style.markers.Circle());
            intersectionSeries.setLineStyle(org.knowm.xchart.style.lines.SeriesLines.NONE);
        }
    }

    /**
     * 📊 Создает пустой график с сообщением об ошибке
     */
    private BufferedImage createEmptyChart(String message) {
        XYChart chart = new org.knowm.xchart.XYChartBuilder()
                .width(ChartUtils.CHART_WIDTH)
                .height(ChartUtils.CHART_HEIGHT)
                .title(message)
                .build();

        // Добавляем минимальные данные для отображения
        chart.addSeries("Нет данных", java.util.Arrays.asList(new Date()), java.util.Arrays.asList(0.0));

        return BitmapEncoder.getBufferedImage(chart);
    }
}