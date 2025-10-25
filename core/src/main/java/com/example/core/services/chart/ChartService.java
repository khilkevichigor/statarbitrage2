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


}