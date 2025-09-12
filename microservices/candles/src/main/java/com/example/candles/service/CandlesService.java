package com.example.candles.service;

import com.example.candles.client.OkxFeignClient;
import com.example.shared.dto.Candle;
import com.example.shared.models.Settings;
import com.example.shared.models.TradingPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandlesService {

    private final OkxFeignClient okxFeignClient;

    public Map<String, List<Candle>> getApplicableCandlesMap(TradingPair tradingPair, Settings settings) {
        Map<String, List<Candle>> candlesMap = getCandles(settings, List.of(tradingPair.getLongTicker(), tradingPair.getShortTicker()), false);
        validateCandlesLimitAndThrow(candlesMap, settings);
        return candlesMap;
    }

    //todo сделать умнее - через кэш или бд - зачем каждую минуту это делать! если объем есть то можно целый день работать, ну или чекать 1раз/час
    public Map<String, List<Candle>> getApplicableCandlesMap(Settings settings, List<String> tradingTickers) {
        List<String> applicableTickers = getApplicableTickers(settings, tradingTickers, "1D", true);
        Map<String, List<Candle>> candlesMap = getCandles(settings, applicableTickers, true);
        validateCandlesLimitAndThrow(candlesMap, settings);
        return candlesMap;
    }

    public Map<String, List<Candle>> getCandles(Settings settings, List<String> swapTickers, boolean isSorted) {
        return okxFeignClient.getCandlesMap(swapTickers, settings.getTimeframe(), (int) settings.getCandleLimit(), isSorted);
    }

    private List<String> getApplicableTickers(Settings settings, List<String> tradingTickers, String timeFrame, boolean isSorted) {
        List<String> swapTickers = okxFeignClient.getAllSwapTickers(isSorted);
        List<String> filteredTickers = swapTickers.stream()
                .filter(ticker -> !tradingTickers.contains(ticker))
                .toList();
        double minVolume = settings.isUseMinVolumeFilter() ? settings.getMinVolume() * 1_000_000 : 0.0;
        return okxFeignClient.getValidTickers(filteredTickers, timeFrame, (int) settings.getCandleLimit(), minVolume, isSorted);
    }

    /**
     * Расширенный метод для получения большого количества свечей с пагинацией
     * Собирает данные через несколько запросов к OKX API для обхода лимита в 300 свечей
     */
    public Map<String, List<Candle>> getCandlesExtended(Settings settings, List<String> swapTickers, int totalLimit) {
        log.info("📊 Расширенный запрос {} свечей для {} тикеров (таймфрейм: {})",
                totalLimit, swapTickers.size(), settings.getTimeframe());

        if (totalLimit <= 300) {
            // Если лимит 300 или меньше, используем стандартный метод
            return getCandles(settings, swapTickers, true);
        }

        Map<String, List<Candle>> result = new HashMap<>();
        int batchSize = 300; // Максимум для OKX API
        int remainingCandles = totalLimit;

        try {
            // Создаем настройки для первой пачки (максимум 300 свечей)
            Settings initialSettings = new Settings();
            initialSettings.copyFrom(settings);
            initialSettings.setCandleLimit(Math.min(batchSize, remainingCandles));

            log.debug("🔄 Получаем первую пачку из {} свечей", initialSettings.getCandleLimit());
            Map<String, List<Candle>> initialBatch = getCandles(initialSettings, swapTickers, true);

            if (initialBatch.isEmpty()) {
                log.warn("⚠️ Не удалось получить начальные данные");
                return result;
            }

            // Для каждого тикера собираем дополнительные исторические данные
            for (Map.Entry<String, List<Candle>> entry : initialBatch.entrySet()) {
                String ticker = entry.getKey();
                List<Candle> allCandles = new ArrayList<>(entry.getValue());

                if (allCandles.isEmpty()) {
                    continue;
                }

                remainingCandles = totalLimit - allCandles.size();

                // Получаем дополнительные исторические данные пачками
                while (remainingCandles > 0 && allCandles.size() < totalLimit) {
                    try {
                        // Получаем timestamp самой старой свечи для пагинации
                        long oldestTimestamp = allCandles.get(0).getTimestamp();

                        // Запрашиваем историческую пачку
                        int batchLimit = Math.min(batchSize, remainingCandles);
                        List<Candle> historicalBatch = getCandlesPaginated(ticker, settings.getTimeframe(),
                                batchLimit, oldestTimestamp);

                        if (historicalBatch.isEmpty()) {
                            log.debug("📉 Нет больше исторических данных для {}", ticker);
                            break;
                        }

                        // Добавляем в начало списка (более старые данные)
                        allCandles.addAll(0, historicalBatch);
                        remainingCandles -= historicalBatch.size();

                        // Небольшая задержка между запросами
                        Thread.sleep(150);

                    } catch (Exception e) {
                        log.warn("⚠️ Ошибка при получении исторических данных для {}: {}",
                                ticker, e.getMessage());
                        break;
                    }
                }

                // Обрезаем до нужного размера (берём самые свежие)
                if (allCandles.size() > totalLimit) {
                    allCandles = allCandles.subList(allCandles.size() - totalLimit, allCandles.size());
                }

                result.put(ticker, allCandles);
            }

            log.info("✅ Расширенный запрос завершен. Получено {} тикеров со средним количеством {} свечей",
                    result.size(),
                    result.values().stream().mapToInt(List::size).average().orElse(0));

        } catch (Exception e) {
            log.error("❌ Ошибка в расширенном запросе свечей: {}", e.getMessage(), e);
            // Fallback к стандартному методу с ограничением в 300 свечей
            log.warn("🔄 Используем fallback к стандартному методу с ограничением 300 свечей");
            Settings fallbackSettings = new Settings();
            fallbackSettings.copyFrom(settings);
            fallbackSettings.setCandleLimit(300);
            return getCandles(fallbackSettings, swapTickers, true);
        }

        return result;
    }

    /**
     * Получение исторических свечей с использованием параметра before для пагинации
     */
    private List<Candle> getCandlesPaginated(String ticker, String timeframe, int limit, long beforeTimestamp) {
        try {
            log.debug("🔍 Запрос {} исторических свечей для {} до timestamp {}",
                    limit, ticker, beforeTimestamp);

            // Вызываем OKX API с параметром before для пагинации
            List<Candle> historicalCandles = okxFeignClient.getCandlesBefore(ticker, timeframe, limit, beforeTimestamp);

            log.debug("📊 Получено {} исторических свечей для {} до timestamp {}",
                    historicalCandles.size(), ticker, beforeTimestamp);

            return historicalCandles;

        } catch (Exception e) {
            log.error("❌ Ошибка при получении исторических свечей для {} до {}: {}",
                    ticker, beforeTimestamp, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap, Settings settings) {
        if (candlesMap == null) {
            throw new IllegalArgumentException("Мапа свечей не может быть null!");
        }

        double candleLimit = settings.getCandleLimit();

        candlesMap.forEach((ticker, candles) -> {
            if (candles == null) {
                log.error("❌ Список свечей для тикера {} равен null!", ticker);
                throw new IllegalArgumentException("Список свечей не может быть null для тикера: " + ticker);
            }
            if (candles.size() != candleLimit) {
                log.error(
                        "❌ Размер списка свечей {} для тикера {} не совпадает с лимитом {}",
                        candles.size(), ticker, candleLimit
                );
                throw new IllegalArgumentException(
                        String.format(
                                "❌ Размер списка свечей для тикера %s: %d, ожидается: %.0f",
                                ticker, candles.size(), candleLimit
                        )
                );
            }
        });
    }
}
