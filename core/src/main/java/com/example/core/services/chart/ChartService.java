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

    private final PixelSpreadService pixelSpreadService;
    private final VerticalChartBuilder verticalChartBuilder;

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
}