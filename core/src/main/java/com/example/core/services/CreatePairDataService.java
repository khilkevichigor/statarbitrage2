package com.example.core.services;

import com.example.core.repositories.PairRepository;
import com.example.core.services.chart.PixelSpreadService;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.PairType;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import com.example.shared.utils.CandlesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePairDataService {

    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final PixelSpreadService pixelSpreadService;
    private final SettingsService settingsService;
    private final PairRepository pairRepository;

    /**
     * Создаёт список торговых пар PairData на основе списка Z-оценок и данных свечей
     */
    public List<Pair> createPairs(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        List<Pair> result = new ArrayList<>();

        for (ZScoreData zScoreData : zScoreDataList) {
            try {
                Pair tradingPair = buildPairData(zScoreData, candlesMap);
                result.add(tradingPair);
            } catch (IllegalArgumentException e) {
                log.warn("⚠️ Пропущена пара {}/{}: {}",
                        zScoreData.getUnderValuedTicker(),
                        zScoreData.getOverValuedTicker(),
                        e.getMessage());
            } catch (Exception e) {
                log.error("❌ Ошибка при создании PairData для пары {}/{}: {}",
                        zScoreData.getUnderValuedTicker(),
                        zScoreData.getOverValuedTicker(),
                        e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * Строит одну торговую пару на основе Z-данных и свечей
     */
    public Pair buildPairData(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {

        //todo серьезное улучшение!!! здесь нужно создавать пару всегда с положительным zScore. Если zScore отрицательный то переварачивать пару и zScoreData.
        // Таким образом мы увеличим конверсию в открытые пары и не будем пропускать пары с высоким но отрицательным zScore

        String undervalued = zScoreData.getUnderValuedTicker();
        String overvalued = zScoreData.getOverValuedTicker();

        List<Candle> undervaluedCandles = candlesMap.get(undervalued);
        List<Candle> overvaluedCandles = candlesMap.get(overvalued);

        if (isEmpty(undervaluedCandles) || isEmpty(overvaluedCandles)) {
            throw new IllegalArgumentException("Недостаточно данных по свечам");
        }

        // Создаём торговую пару с типом TRADING
        Pair tradingPair = Pair.builder()
                .type(PairType.TRADING)
                .status(TradeStatus.SELECTED)
                .tickerA(undervalued)  // Long ticker
                .tickerB(overvalued)   // Short ticker
                .pairName(undervalued + "/" + overvalued)
                .longTickerCurrentPrice(BigDecimal.valueOf(CandlesUtil.getLastClose(undervaluedCandles)))
                .shortTickerCurrentPrice(BigDecimal.valueOf(CandlesUtil.getLastClose(overvaluedCandles)))
                .timestamp(System.currentTimeMillis())
                .entryTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .searchDate(LocalDateTime.now())
                .build();

        // Устанавливаем свечи
        tradingPair.setLongTickerCandles(undervaluedCandles);
        tradingPair.setShortTickerCandles(overvaluedCandles);

        // Переносим данные стабильности из исходной стабильной пары
        transferStabilityDataFromStablePair(tradingPair, undervalued, overvalued);

        // Заполняем поля настроек сразу при создании пары
        Settings settings = settingsService.getSettings();
        tradingPair.setSettingsCandleLimit(BigDecimal.valueOf(settings.getCandleLimit()));
        tradingPair.setSettingsMinZ(BigDecimal.valueOf(settings.getMinZ()));
        tradingPair.setTimeframe(settings.getTimeframe());

        // Устанавливаем минимальный объем из настроек
        tradingPair.setMinVolMln(BigDecimal.valueOf(settings.getMinVolume()));

        updateZScoreDataCurrentService.updateCurrent(tradingPair, zScoreData);

        // Рассчитываем пиксельный спред для новой пары
        try {
            pixelSpreadService.calculatePixelSpreadIfNeeded(tradingPair);

            // Логируем статистику пиксельного спреда
            double avgSpread = pixelSpreadService.getAveragePixelSpread(tradingPair);
            double maxSpread = pixelSpreadService.getMaxPixelSpread(tradingPair);
            double currentSpread = pixelSpreadService.getCurrentPixelSpread(tradingPair);

            log.debug("🔢 Пиксельный спред для {}/{}: avg={}px, max={}px, current={}px",
                    tradingPair.getLongTicker(), tradingPair.getShortTicker(),
                    String.format("%.1f", avgSpread), String.format("%.1f", maxSpread),
                    String.format("%.1f", currentSpread));

        } catch (Exception e) {
            log.warn("⚠️ Ошибка расчета пиксельного спреда для {}/{}: {}",
                    tradingPair.getLongTicker(), tradingPair.getShortTicker(), e.getMessage());
        }

        return tradingPair;
    }

    /**
     * Переносит данные стабильности из исходной стабильной пары в новую торговую пару
     */
    private void transferStabilityDataFromStablePair(Pair tradingPair, String tickerA, String tickerB) {
        try {
            // Ищем стабильную пару с такими же тикерами
            List<Pair> stablePairs = pairRepository.findByTickerAAndTickerB(tickerA, tickerB);

            // Также проверяем обратное направление (зеркальную пару)
            if (stablePairs.isEmpty()) {
                stablePairs = pairRepository.findByTickerAAndTickerB(tickerB, tickerA);
            }

            // Фильтруем только стабильные пары и ищем лучшую по скору
            Pair bestStablePair = stablePairs.stream()
                    .filter(p -> PairType.STABLE.equals(p.getType()))
                    .filter(p -> p.getTotalScore() != null)
                    .max((p1, p2) -> {
                        // Сравниваем по скору, приоритет отдаем парам в мониторинге
                        if (p1.isInMonitoring() && !p2.isInMonitoring()) return 1;
                        if (!p1.isInMonitoring() && p2.isInMonitoring()) return -1;
                        return Integer.compare(p1.getTotalScore(), p2.getTotalScore());
                    })
                    .orElse(null);

            if (bestStablePair != null) {
                // Переносим данные стабильности
                tradingPair.setTotalScore(bestStablePair.getTotalScore());
                tradingPair.setTotalScoreEntry(bestStablePair.getTotalScore()); // При создании entry = current
                tradingPair.setStabilityRating(bestStablePair.getStabilityRating());

                // Переносим дополнительные поля стабильности если они есть
                if (bestStablePair.getDataPoints() != null) {
                    tradingPair.setDataPoints(bestStablePair.getDataPoints());
                }
                if (bestStablePair.getCandleCount() != null) {
                    tradingPair.setCandleCount(bestStablePair.getCandleCount());
                }
                if (bestStablePair.getTimeframe() != null) {
                    tradingPair.setTimeframe(bestStablePair.getTimeframe());
                }
                if (bestStablePair.getPeriod() != null) {
                    tradingPair.setPeriod(bestStablePair.getPeriod());
                }
                if (bestStablePair.getMinVolMln() != null) {
                    tradingPair.setMinVolMln(bestStablePair.getMinVolMln());
                }

                log.debug("✅ Перенесены данные стабильности для {}/{}: скор={}, рейтинг={}, источник={}",
                        tickerA, tickerB, bestStablePair.getTotalScore(),
                        bestStablePair.getStabilityRating(),
                        bestStablePair.isInMonitoring() ? "мониторинг" : "найденные");

            } else {
                log.debug("⚠️ Не найдена стабильная пара для переноса данных: {}/{}", tickerA, tickerB);
            }

        } catch (Exception e) {
            log.warn("❌ Ошибка при переносе данных стабильности для {}/{}: {}",
                    tickerA, tickerB, e.getMessage());
        }
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
