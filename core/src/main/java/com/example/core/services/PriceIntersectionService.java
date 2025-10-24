package com.example.core.services;

import com.example.core.services.chart.ChartService;
import com.example.shared.dto.Candle;
import com.example.shared.models.Pair;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Сервис для подсчета пересечений нормализованных цен закрытия
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceIntersectionService {

    private final ChartService chartService;

    /**
     * Подсчитывает количество пересечений нормализованных цен закрытия для пары
     * и возвращает результат с нормализованными ценами
     *
     * @param cointPair пара для анализа
     * @return результат с количеством пересечений и нормализованными ценами
     */
    public IntersectionResult calculateIntersectionsWithData(Pair cointPair) {
        List<Candle> longCandles = cointPair.getLongTickerCandles();
        List<Candle> shortCandles = cointPair.getShortTickerCandles();

        if (longCandles == null || shortCandles == null ||
                longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Отсутствуют данные свечей для пары {}: long={}, short={}",
                    cointPair.getPairName(),
                    longCandles != null ? longCandles.size() : "null",
                    shortCandles != null ? shortCandles.size() : "null");
            return new IntersectionResult(0, null, null);
        }

        int minSize = Math.min(longCandles.size(), shortCandles.size());
        if (minSize < 2) {
            log.warn("⚠️ Недостаточно данных для анализа пересечений пары {}: minSize={}",
                    cointPair.getPairName(), minSize);
            return new IntersectionResult(0, null, null);
        }

        try {
            // Нормализация цен
            double[] normalizedLongPrices = normalizePrices(longCandles, minSize);
            double[] normalizedShortPrices = normalizePrices(shortCandles, minSize);

            // Подсчет пересечений
            int intersections = countIntersections(normalizedLongPrices, normalizedShortPrices);

            log.info("📊 Пара {}: найдено {} пересечений нормализованных цен из {} точек данных",
                    cointPair.getPairName(), intersections, minSize);

            return new IntersectionResult(intersections, normalizedLongPrices, normalizedShortPrices);

        } catch (Exception e) {
            log.error("❌ Ошибка при подсчете пересечений для пары {}: {}",
                    cointPair.getPairName(), e.getMessage(), e);
            return new IntersectionResult(0, null, null);
        }
    }

    /**
     * Подсчитывает количество пересечений нормализованных цен закрытия для пары
     *
     * @param cointPair пара для анализа
     * @return количество пересечений
     */
    public int calculateIntersections(Pair cointPair) {
        List<Candle> longCandles = cointPair.getLongTickerCandles();
        List<Candle> shortCandles = cointPair.getShortTickerCandles();

        if (longCandles == null || shortCandles == null ||
                longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Отсутствуют данные свечей для пары {}: long={}, short={}",
                    cointPair.getPairName(),
                    longCandles != null ? longCandles.size() : "null",
                    shortCandles != null ? shortCandles.size() : "null");
            return 0;
        }

        int minSize = Math.min(longCandles.size(), shortCandles.size());
        if (minSize < 2) {
            log.warn("⚠️ Недостаточно данных для анализа пересечений пары {}: minSize={}",
                    cointPair.getPairName(), minSize);
            return 0;
        }

        try {
            // Нормализация цен
            double[] normalizedLongPrices = normalizePrices(longCandles, minSize);
            double[] normalizedShortPrices = normalizePrices(shortCandles, minSize);

            // Подсчет пересечений
            int intersections = countIntersections(normalizedLongPrices, normalizedShortPrices);

            log.info("📊 Пара {}: найдено {} пересечений нормализованных цен из {} точек данных",
                    cointPair.getPairName(), intersections, minSize);

            return intersections;

        } catch (Exception e) {
            log.error("❌ Ошибка при подсчете пересечений для пары {}: {}",
                    cointPair.getPairName(), e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Нормализует цены закрытия по формуле: normValue = (x - min) / (max - min)
     *
     * @param candles список свечей
     * @param size    количество элементов для обработки
     * @return массив нормализованных цен
     */
    private double[] normalizePrices(List<Candle> candles, int size) {
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
     * Подсчитывает количество пересечений между двумя нормализованными ценовыми рядами
     *
     * @param prices1 первый ценовой ряд
     * @param prices2 второй ценовой ряд
     * @return количество пересечений
     */
    private int countIntersections(double[] prices1, double[] prices2) {
        int intersections = 0;

        if (prices1.length != prices2.length || prices1.length < 2) {
            return 0;
        }

        // Определяем начальное положение (кто выше)
        boolean firstAboveSecond = prices1[0] > prices2[0];

        // Проходим по всем точкам и считаем смену положения
        for (int i = 1; i < prices1.length; i++) {
            boolean currentFirstAboveSecond = prices1[i] > prices2[i];

            // Если положение изменилось - это пересечение
            if (currentFirstAboveSecond != firstAboveSecond) {
                intersections++;
                firstAboveSecond = currentFirstAboveSecond;
            }
        }

        return intersections;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IntersectionResult {
        private int intersections;
        private double[] normalizedLongPrices;
        private double[] normalizedShortPrices;
    }

    /**
     * Подсчитывает пересечения и создает чарт нормализованных цен с пересечениями
     *
     * @param cointPair   пара для анализа
     * @param createChart флаг создания чарта (можно использовать для отключения)
     * @return количество пересечений
     */
    public int calculateIntersectionsWithChart(Pair cointPair, boolean createChart) {
        List<Candle> longCandles = cointPair.getLongTickerCandles();
        List<Candle> shortCandles = cointPair.getShortTickerCandles();

        if (longCandles == null || shortCandles == null ||
                longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Отсутствуют данные свечей для пары {}: long={}, short={}",
                    cointPair.getPairName(),
                    longCandles != null ? longCandles.size() : "null",
                    shortCandles != null ? shortCandles.size() : "null");
            return 0;
        }

        int minSize = Math.min(longCandles.size(), shortCandles.size());
        if (minSize < 2) {
            log.warn("⚠️ Недостаточно данных для анализа пересечений пары {}: minSize={}",
                    cointPair.getPairName(), minSize);
            return 0;
        }

        try {
            // Подсчет пересечений с получением нормализованных цен
            IntersectionResult result = calculateIntersectionsWithData(cointPair);
            int intersections = result.getIntersections();

            // Создаем чарт если требуется
            if (createChart) {
                log.info("📊 Создание чарта нормализованных цен для пары {} с {} пересечениями",
                        cointPair.getPairName(), intersections);

                BufferedImage chartImage = chartService.createNormalizedPriceIntersectionsChart(
                        longCandles, shortCandles, cointPair.getPairName(), intersections, true);

                if (chartImage.getWidth() > 1 && chartImage.getHeight() > 1) {
                    log.info("✅ Чарт нормализованных цен создан для пары {} (пересечений: {}, точек данных: {})",
                            cointPair.getPairName(), intersections, minSize);
                } else {
                    log.warn("⚠️ Не удалось создать чарт для пары {}", cointPair.getPairName());
                }
            }

            return intersections;

        } catch (Exception e) {
            log.error("❌ Ошибка при подсчете пересечений с чартом для пары {}: {}",
                    cointPair.getPairName(), e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Создает чарт пересечений нормализованных цен и возвращает его как массив байт
     *
     * @param cointPair пара для анализа
     * @return массив байт чарта в формате PNG или пустой массив в случае ошибки
     */
    public byte[] getIntersectionChartAsBytes(Pair cointPair) {
        List<Candle> longCandles = cointPair.getLongTickerCandles();
        List<Candle> shortCandles = cointPair.getShortTickerCandles();

        if (longCandles == null || shortCandles == null ||
                longCandles.isEmpty() || shortCandles.isEmpty()) {
            log.warn("⚠️ Отсутствуют данные свечей для создания чарта пары {}: long={}, short={}",
                    cointPair.getPairName(),
                    longCandles != null ? longCandles.size() : "null",
                    shortCandles != null ? shortCandles.size() : "null");
            return new byte[0];
        }

        try {
            // Подсчитываем пересечения для получения количества
            IntersectionResult result = calculateIntersectionsWithData(cointPair);
            int intersections = result.getIntersections();

            log.info("📊 Создание чарта пересечений для отправки в Telegram: пара {}, пересечений {}",
                    cointPair.getPairName(), intersections);

            // Создаем чарт без сохранения в файл
            BufferedImage chartImage = chartService.createNormalizedPriceIntersectionsChart(
                    longCandles, shortCandles, cointPair.getPairName(), intersections, false);

            if (chartImage.getWidth() <= 1 || chartImage.getHeight() <= 1) {
                log.warn("⚠️ Не удалось создать чарт для пары {}", cointPair.getPairName());
                return new byte[0];
            }

            // Конвертируем BufferedImage в массив байт
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(chartImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            log.info("✅ Чарт пересечений создан и конвертирован в байты: пара {}, размер {} байт",
                    cointPair.getPairName(), imageBytes.length);

            return imageBytes;

        } catch (Exception e) {
            log.error("❌ Ошибка при создании чарта пересечений как массив байт для пары {}: {}",
                    cointPair.getPairName(), e.getMessage(), e);
            return new byte[0];
        }
    }
}