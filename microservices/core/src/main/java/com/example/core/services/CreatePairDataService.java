package com.example.core.services;

import com.example.shared.dto.Candle;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.TradingPair;
import com.example.shared.utils.CandlesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePairDataService {

    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final PixelSpreadService pixelSpreadService;

    /**
     * Создаёт список торговых пар PairData на основе списка Z-оценок и данных свечей
     */
    public List<TradingPair> createPairs(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        List<TradingPair> result = new ArrayList<>();

        for (ZScoreData zScoreData : zScoreDataList) {
            try {
                TradingPair tradingPair = buildPairData(zScoreData, candlesMap);
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
     * Строит одну пару на основе Z-данных и свечей
     */
    private TradingPair buildPairData(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
        String undervalued = zScoreData.getUnderValuedTicker();
        String overvalued = zScoreData.getOverValuedTicker();

        List<Candle> undervaluedCandles = candlesMap.get(undervalued);
        List<Candle> overvaluedCandles = candlesMap.get(overvalued);

        if (isEmpty(undervaluedCandles) || isEmpty(overvaluedCandles)) {
            throw new IllegalArgumentException("Недостаточно данных по свечам");
        }

        TradingPair tradingPair = new TradingPair(undervalued, overvalued);
        tradingPair.setStatus(TradeStatus.SELECTED);
        tradingPair.setLongTickerCurrentPrice(CandlesUtil.getLastClose(undervaluedCandles));
        tradingPair.setShortTickerCurrentPrice(CandlesUtil.getLastClose(overvaluedCandles));
        tradingPair.setLongTickerCandles(undervaluedCandles);
        tradingPair.setShortTickerCandles(overvaluedCandles);
        tradingPair.setTimestamp(System.currentTimeMillis()); //создание и обноаление

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

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
