package com.example.core.services.chart;

import com.example.shared.models.Pair;
import com.example.shared.enums.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 🎯 Билдер для создания вертикальной компоновки чартов
 * Создает один длинный чарт из нескольких секций без наложений
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerticalChartBuilder {

    private final ZScoreChartBuilder zScoreChartBuilder;
    private final ChartLayerService chartLayerService;

    /**
     * 🏗️ Создает вертикально скомпонованный чарт с секциями
     * 🎯 УПРОЩЕНИЕ: Основные секции отображаются ВСЕГДА! Никаких чекбоксов!
     * Секции: 1) Нормализованные цены, 2) Z-Score, 3) Профит (только для TRADING)
     *
     * @param tradingPair    Торговая пара
     * @param showEntryPoint Показать точки входа на всех секциях
     * @return Готовое изображение вертикального чарта (2-3 секции)
     */
    public BufferedImage createVerticalChart(Pair tradingPair, boolean showEntryPoint) {

        log.debug("🏗️ Создание вертикального чарта для пары: {} (статус: {}, EntryPoint: {})",
                tradingPair.getPairName(), tradingPair.getStatus(), showEntryPoint);

        List<BufferedImage> chartSections = new ArrayList<>();

        // 1. Чарт нормализованных цен - ВСЕГДА как основа
        BufferedImage priceChart = createPriceSection(tradingPair, showEntryPoint, false); // НЕ последний
        if (priceChart != null) {
            chartSections.add(priceChart);
            log.debug("✅ Добавлена секция цен");
        }

        // 2. Z-Score чарт - ВСЕГДА
        // Проверяем, будет ли добавлен профит, чтобы определить, должен ли Z-Score быть последним
        boolean isTrading = TradeStatus.TRADING.equals(tradingPair.getStatus());
        boolean zScoreIsLast = !isTrading; // Z-Score последний, если нет профита
        
        BufferedImage zScoreChart = createZScoreSection(tradingPair, showEntryPoint, zScoreIsLast);
        if (zScoreChart != null) {
            chartSections.add(zScoreChart);
            log.debug("✅ Добавлена секция Z-Score (последняя: {})", zScoreIsLast);
        }

        // 3. Профит - ТОЛЬКО для торгуемых пар (статус TRADING)
        if (isTrading) {
            BufferedImage profitChart = createProfitSection(tradingPair, showEntryPoint, true); // ПОСЛЕДНИЙ с шкалой X
            if (profitChart != null) {
                chartSections.add(profitChart);
                log.debug("✅ Добавлена секция профита для торгуемой пары (последняя, с шкалой X)");
            }
        } else {
            log.debug("⏭️ Секция профита пропущена - пара не в торговле (статус: {})", 
                    tradingPair.getStatus());
        }

        // Если нет секций - создаем минимальный чарт с ценами и шкалой X
        if (chartSections.isEmpty()) {
            log.warn("⚠️ Ни одна секция не была создана, возвращаем чарт цен");
            return createPriceSection(tradingPair, showEntryPoint, true); // ПОСЛЕДНИЙ
        }

        // Объединяем все секции в один вертикальный чарт
        BufferedImage result = combineChartsVertically(chartSections);
        log.debug("✅ Вертикальный чарт создан для пары {} ({} секций)",
                tradingPair.getPairName(), chartSections.size());

        return result;
    }

    /**
     * 💰 Создает секцию с нормализованными ценами
     *
     * @param isLast если true - показывать шкалу X, если false - скрывать
     */
    private BufferedImage createPriceSection(Pair tradingPair, boolean showEntryPoint, boolean isLast) {
        try {
            log.debug("💰 Создание секции нормализованных цен (шкала X: {})", isLast ? "показать" : "скрыть");

            // Создаем чистый чарт для нормализованных цен без наложения и прозрачности
            org.knowm.xchart.XYChart chart = zScoreChartBuilder.buildCleanNormalizedPriceChart(tradingPair, showEntryPoint);

            // Добавляем нормализованные синхронизированные цены с точкой входа
            chartLayerService.addSynchronizedPricesToChart(chart, tradingPair, true, showEntryPoint);

            // 🎯 Управляем отображением шкалы X
            chart.getStyler().setXAxisTicksVisible(isLast);
            chart.getStyler().setXAxisTitleVisible(isLast);

            // Обновляем заголовок
            chart.setTitle("💰 Нормализованные цены: " + tradingPair.getPairName());

            return org.knowm.xchart.BitmapEncoder.getBufferedImage(chart);

        } catch (Exception e) {
            log.error("❌ Ошибка при создании секции цен: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 📊 Создает секцию Z-Score
     *
     * @param isLast если true - показывать шкалу X, если false - скрывать
     */
    private BufferedImage createZScoreSection(Pair tradingPair, boolean showEntryPoint, boolean isLast) {
        try {
            log.debug("📊 Создание секции Z-Score (шкала X: {})", isLast ? "показать" : "скрыть");

            // Создаем чистый Z-Score чарт
            org.knowm.xchart.XYChart chart = zScoreChartBuilder.buildBasicZScoreChart(tradingPair, showEntryPoint);

            // 🎯 Управляем отображением шкалы X
            chart.getStyler().setXAxisTicksVisible(isLast);
            chart.getStyler().setXAxisTitleVisible(isLast);

            // Обновляем заголовок
            chart.setTitle("📊 Z-Score: " + tradingPair.getPairName());

            return org.knowm.xchart.BitmapEncoder.getBufferedImage(chart);

        } catch (Exception e) {
            log.error("❌ Ошибка при создании секции Z-Score: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 💹 Создает секцию профита
     *
     * @param isLast если true - показывать шкалу X, если false - скрывать
     */
    private BufferedImage createProfitSection(Pair tradingPair, boolean showEntryPoint, boolean isLast) {
        try {
            log.debug("💹 Создание секции профита (шкала X: {})", isLast ? "показать" : "скрыть");

            // Создаем базовый чарт и добавляем профит
            org.knowm.xchart.XYChart chart = zScoreChartBuilder.buildBasicZScoreChart(tradingPair, showEntryPoint);

            // 🎯 Удаляем Z-Score серию И горизонтальные линии уровней, оставляем только точки входа
            removeZScoreSeriesButKeepEntry(chart);

            // Добавляем профит
            chartLayerService.addSynchronizedProfitToChart(chart, tradingPair);

            // 🎯 Управляем отображением шкалы X
            chart.getStyler().setXAxisTicksVisible(isLast);
            chart.getStyler().setXAxisTitleVisible(isLast);

            // Обновляем заголовок
            chart.setTitle("💹 Профит: " + tradingPair.getPairName());

            return org.knowm.xchart.BitmapEncoder.getBufferedImage(chart);

        } catch (Exception e) {
            log.error("❌ Ошибка при создании секции профита: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 🗑️ Удаляет Z-Score серию И горизонтальные линии уровней из чарта, но сохраняет точки входа
     * 🎯 ИСПРАВЛЕНИЕ: Горизонтальные линии -3, -2, -1, 0, 1, 2, 3 должны быть ТОЛЬКО на Z-Score секции!
     */
    private void removeZScoreSeriesButKeepEntry(org.knowm.xchart.XYChart chart) {
        try {
            // Удаляем Z-Score серию
            chart.removeSeries("Z-Score");
            log.debug("✅ Удалена Z-Score серия");
        } catch (Exception e) {
            // Игнорируем ошибки - серия может не существовать
            log.debug("Z-Score серия не найдена для удаления");
        }

        // 🎯 НОВОЕ: Удаляем ВСЕ горизонтальные линии Z-Score уровней
        removeZScoreHorizontalLines(chart);
    }

    /**
     * 🗑️ Удаляет все горизонтальные линии Z-Score уровней (-3, -2, -1, 0, 1, 2, 3)
     * 🎯 Эти линии должны быть ТОЛЬКО на Z-Score секции!
     */
    private void removeZScoreHorizontalLines(org.knowm.xchart.XYChart chart) {
        // Удаляем горизонтальные линии для всех стандартных уровней Z-Score
        double[] levels = {-3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0};

        for (double level : levels) {
            try {
                String seriesName = "level_" + level;
                chart.removeSeries(seriesName);
                log.debug("✅ Удалена горизонтальная линия уровня {}", level);
            } catch (Exception e) {
                // Игнорируем ошибки - серия может не существовать
                log.trace("Горизонтальная линия уровня {} не найдена", level);
            }
        }

        log.debug("🎯 Все горизонтальные линии Z-Score уровней удалены из секции");
    }

    /**
     * 📐 Объединяет список чартов в один вертикальный чарт
     */
    private BufferedImage combineChartsVertically(List<BufferedImage> chartSections) {
        if (chartSections.isEmpty()) {
            log.warn("⚠️ Нет секций для объединения");
            return null;
        }

        if (chartSections.size() == 1) {
            log.debug("📐 Только одна секция - возвращаем как есть");
            return chartSections.get(0);
        }

        try {
            // Вычисляем размеры итогового изображения
            int totalWidth = chartSections.get(0).getWidth();
            int totalHeight = chartSections.stream().mapToInt(BufferedImage::getHeight).sum();

            log.debug("📐 Объединение {} секций в чарт размером {}x{}",
                    chartSections.size(), totalWidth, totalHeight);

            // Создаем итоговое изображение
            BufferedImage combinedImage = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = combinedImage.createGraphics();

            // Устанавливаем качественный рендеринг
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Рисуем каждую секцию одну под другой без разрывов
            int currentY = 0;
            for (int i = 0; i < chartSections.size(); i++) {
                BufferedImage section = chartSections.get(i);
                g2d.drawImage(section, 0, currentY, null);
                currentY += section.getHeight();

                log.debug("📐 Секция {} размещена на позиции Y={}", i + 1, currentY - section.getHeight());
            }

            g2d.dispose();

            log.debug("✅ Вертикальный чарт объединен успешно");
            return combinedImage;

        } catch (Exception e) {
            log.error("❌ Ошибка при объединении чартов: {}", e.getMessage(), e);
            return chartSections.get(0); // Возвращаем первую секцию в случае ошибки
        }
    }
}