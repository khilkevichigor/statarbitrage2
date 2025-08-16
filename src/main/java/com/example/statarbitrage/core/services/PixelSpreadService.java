package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.PixelSpreadHistoryItem;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class PixelSpreadService {

    /**
     * Вычисляет пиксельный спред для пары, если он еще не вычислен
     */
    public void calculatePixelSpreadIfNeeded(PairData pairData) {
        if (pairData.getPixelSpreadHistory().isEmpty()) {
            log.debug("🔢 Пиксельный спред не вычислен, вычисляем для пары {}", pairData.getPairName());
            calculatePixelSpreadForPair(pairData);
        }
    }

    /**
     * Принудительно пересчитывает пиксельный спред для пары
     */
    public void recalculatePixelSpread(PairData pairData) {
        log.debug("🔢 Пересчитываем пиксельный спред для пары {}", pairData.getPairName());
        pairData.clearPixelSpreadHistory(); // Очищаем существующую историю
        calculatePixelSpreadForPair(pairData);
    }

    /**
     * Вычисляет пиксельный спред для пары
     */
    private void calculatePixelSpreadForPair(PairData pairData) {
        List<Candle> longCandles = pairData.getLongTickerCandles();
        List<Candle> shortCandles = pairData.getShortTickerCandles();
        List<ZScoreParam> history = pairData.getZScoreHistory();

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

        log.debug("✅ Вычисляем пиксельный спред: LONG {} точек (диапазон: {}-{}), SHORT {} точек (диапазон: {}-{})",
                scaledLongPrices.size(), minLongPrice, maxLongPrice,
                scaledShortPrices.size(), minShortPrice, maxShortPrice);

        // Вычисляем пиксельное расстояние между графиками long и short
        calculateAndSavePixelSpread(pairData, timeLong, scaledLongPrices, timeShort, scaledShortPrices);
    }

    /**
     * Вычисляет пиксельное расстояние между графиками Long и Short цен и сохраняет в историю
     */
    private void calculateAndSavePixelSpread(PairData pairData, List<Date> timeLong, List<Double> scaledLongPrices,
                                             List<Date> timeShort, List<Double> scaledShortPrices) {
        log.debug("🔢 Начинаем вычисление пиксельного спреда для пары {}", pairData.getPairName());

        if (timeLong.isEmpty() || timeShort.isEmpty() || scaledLongPrices.isEmpty() || scaledShortPrices.isEmpty()) {
            log.warn("⚠️ Недостаточно данных для вычисления пиксельного спреда");
            return;
        }

        int chartHeight = 720; // Высота чарта

        // Находим диапазон масштабированных значений
        double minValue = Math.min(
                scaledLongPrices.stream().min(Double::compareTo).orElse(0.0),
                scaledShortPrices.stream().min(Double::compareTo).orElse(0.0)
        );
        double maxValue = Math.max(
                scaledLongPrices.stream().max(Double::compareTo).orElse(1.0),
                scaledShortPrices.stream().max(Double::compareTo).orElse(1.0)
        );

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
                pairData.addPixelSpreadPoint(pixelSpreadItem);

                log.trace("🔢 Timestamp: {}, Long: {} px, Short: {} px, Distance: {} px",
                        new Date(timestamp), Math.round(longPixelY), Math.round(shortPixelY), Math.round(pixelDistance));
            }
        }

        log.debug("✅ Пиксельный спред вычислен и сохранен. Всего точек: {}",
                pairData.getPixelSpreadHistory().size());
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
     * Получает среднее значение пиксельного спреда для пары
     */
    public double getAveragePixelSpread(PairData pairData) {
        List<PixelSpreadHistoryItem> history = pairData.getPixelSpreadHistory();
        if (history.isEmpty()) {
            return 0.0;
        }

        return history.stream()
                .mapToDouble(PixelSpreadHistoryItem::getPixelDistance)
                .average()
                .orElse(0.0);
    }

    /**
     * Получает максимальное значение пиксельного спреда для пары
     */
    public double getMaxPixelSpread(PairData pairData) {
        List<PixelSpreadHistoryItem> history = pairData.getPixelSpreadHistory();
        if (history.isEmpty()) {
            return 0.0;
        }

        return history.stream()
                .mapToDouble(PixelSpreadHistoryItem::getPixelDistance)
                .max()
                .orElse(0.0);
    }

    /**
     * Получает минимальное значение пиксельного спреда для пары
     */
    public double getMinPixelSpread(PairData pairData) {
        List<PixelSpreadHistoryItem> history = pairData.getPixelSpreadHistory();
        if (history.isEmpty()) {
            return 0.0;
        }

        return history.stream()
                .mapToDouble(PixelSpreadHistoryItem::getPixelDistance)
                .min()
                .orElse(0.0);
    }

    /**
     * Получает текущее значение пиксельного спреда для пары (последнее по времени)
     */
    public double getCurrentPixelSpread(PairData pairData) {
        List<PixelSpreadHistoryItem> history = pairData.getPixelSpreadHistory();
        if (history.isEmpty()) {
            return 0.0;
        }

        // Сортируем по времени и берем последнее значение
        return history.stream()
                .max(Comparator.comparing(PixelSpreadHistoryItem::getTimestamp))
                .map(PixelSpreadHistoryItem::getPixelDistance)
                .orElse(0.0);
    }

    /**
     * Получает стандартное отклонение пиксельного спреда для пары
     */
    public double getPixelSpreadStandardDeviation(PairData pairData) {
        List<PixelSpreadHistoryItem> history = pairData.getPixelSpreadHistory();
        if (history.size() < 2) {
            return 0.0;
        }

        double average = getAveragePixelSpread(pairData);

        double sumSquaredDifferences = history.stream()
                .mapToDouble(item -> {
                    double diff = item.getPixelDistance() - average;
                    return diff * diff;
                })
                .sum();

        return Math.sqrt(sumSquaredDifferences / (history.size() - 1));
    }

    /**
     * Добавляет новую точку пиксельного спреда для текущего времени
     */
    public void addCurrentPixelSpreadPoint(PairData pairData) {
        List<Candle> longCandles = pairData.getLongTickerCandles();
        List<Candle> shortCandles = pairData.getShortTickerCandles();
        List<ZScoreParam> history = pairData.getZScoreHistory();

        if (longCandles == null || shortCandles == null || longCandles.isEmpty() || shortCandles.isEmpty() || history.isEmpty()) {
            log.warn("⚠️ Не найдены данные для добавления новой точки пиксельного спреда: longCandles={}, shortCandles={}, history={}",
                    longCandles != null ? longCandles.size() : "null",
                    shortCandles != null ? shortCandles.size() : "null",
                    history.size());
            return;
        }

        // Получаем текущее время - последний Z-Score
        ZScoreParam latestZScore = history.get(history.size() - 1);
        long currentTimestamp = latestZScore.getTimestamp();

        // Получаем последние цены
        Candle latestLongCandle = longCandles.stream()
                .max(Comparator.comparing(Candle::getTimestamp))
                .orElse(null);
        Candle latestShortCandle = shortCandles.stream()
                .max(Comparator.comparing(Candle::getTimestamp))
                .orElse(null);

        if (latestLongCandle == null || latestShortCandle == null) {
            log.warn("⚠️ Не найдены последние свечи для добавления точки пиксельного спреда");
            return;
        }

        // Получаем диапазон Z-Score для нормализации
        double minZScore = history.stream().mapToDouble(ZScoreParam::getZscore).min().orElse(-3.0);
        double maxZScore = history.stream().mapToDouble(ZScoreParam::getZscore).max().orElse(3.0);
        double zRange = maxZScore - minZScore;

        // Получаем диапазон цен для нормализации
        double minLongPrice = longCandles.stream().mapToDouble(Candle::getClose).min().orElse(0.0);
        double maxLongPrice = longCandles.stream().mapToDouble(Candle::getClose).max().orElse(1.0);
        double longPriceRange = maxLongPrice - minLongPrice;

        double minShortPrice = shortCandles.stream().mapToDouble(Candle::getClose).min().orElse(0.0);
        double maxShortPrice = shortCandles.stream().mapToDouble(Candle::getClose).max().orElse(1.0);
        double shortPriceRange = maxShortPrice - minShortPrice;

        // Нормализуем текущие цены в диапазон Z-Score
        double scaledLongPrice = longPriceRange != 0 ?
                minZScore + ((latestLongCandle.getClose() - minLongPrice) / longPriceRange) * zRange : minZScore;
        double scaledShortPrice = shortPriceRange != 0 ?
                minZScore + ((latestShortCandle.getClose() - minShortPrice) / shortPriceRange) * zRange : minZScore;

        // Вычисляем пиксельное расстояние
        int chartHeight = 720;
        double minValue = Math.min(scaledLongPrice, scaledShortPrice);
        double maxValue = Math.max(scaledLongPrice, scaledShortPrice);

        double longPixelY = convertValueToPixelY(scaledLongPrice, minZScore, maxZScore, chartHeight);
        double shortPixelY = convertValueToPixelY(scaledShortPrice, minZScore, maxZScore, chartHeight);
        double pixelDistance = Math.abs(longPixelY - shortPixelY);

        // Проверяем, не добавляли ли мы уже точку для этого времени
        boolean alreadyExists = pairData.getPixelSpreadHistory().stream()
                .anyMatch(item -> Math.abs(item.getTimestamp() - currentTimestamp) < 1000); // 1 секунда толеранс

        if (!alreadyExists) {
            PixelSpreadHistoryItem newPoint = new PixelSpreadHistoryItem(currentTimestamp, pixelDistance);
            pairData.addPixelSpreadPoint(newPoint);

            log.debug("✅ Добавлена новая точка пиксельного спреда для пары {} в {}: {} пикселей",
                    pairData.getPairName(), new Date(currentTimestamp), Math.round(pixelDistance));
        } else {
            log.trace("⏭️ Точка пиксельного спреда уже существует для времени {} пары {}",
                    new Date(currentTimestamp), pairData.getPairName());
        }
    }

    /**
     * Конвертирует значение в пиксельную координату Y (перевернутая система координат)
     */
    private double convertValueToPixelY(double value, double minValue, double maxValue, int chartHeight) {
        if (maxValue - minValue == 0) return chartHeight / 2.0;

        // Нормализуем значение в диапазон [0, 1]
        double normalized = (value - minValue) / (maxValue - minValue);

        // Конвертируем в пиксели (Y=0 вверху, Y=chartHeight внизу)
        return chartHeight - (normalized * chartHeight);
    }
}