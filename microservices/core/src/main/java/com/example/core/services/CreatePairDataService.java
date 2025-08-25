package com.example.core.services;

import com.example.shared.dto.ZScoreData;
import com.example.shared.models.Candle;
import com.example.shared.models.PairData;
import com.example.shared.models.TradeStatus;
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
    public List<PairData> createPairs(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        List<PairData> result = new ArrayList<>();

        for (ZScoreData zScoreData : zScoreDataList) {
            try {
                PairData pairData = buildPairData(zScoreData, candlesMap);
                result.add(pairData);
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
    private PairData buildPairData(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
        String undervalued = zScoreData.getUnderValuedTicker();
        String overvalued = zScoreData.getOverValuedTicker();

        List<Candle> undervaluedCandles = candlesMap.get(undervalued);
        List<Candle> overvaluedCandles = candlesMap.get(overvalued);

        if (isEmpty(undervaluedCandles) || isEmpty(overvaluedCandles)) {
            throw new IllegalArgumentException("Недостаточно данных по свечам");
        }

        PairData pairData = new PairData(undervalued, overvalued);
        pairData.setStatus(TradeStatus.SELECTED);
        pairData.setLongTickerCurrentPrice(CandlesUtil.getLastClose(undervaluedCandles));
        pairData.setShortTickerCurrentPrice(CandlesUtil.getLastClose(overvaluedCandles));
        pairData.setLongTickerCandles(undervaluedCandles);
        pairData.setShortTickerCandles(overvaluedCandles);
        pairData.setTimestamp(System.currentTimeMillis()); //создание и обноаление

        updateZScoreDataCurrentService.updateCurrent(pairData, zScoreData);

        // Рассчитываем пиксельный спред для новой пары
        try {
            pixelSpreadService.calculatePixelSpreadIfNeeded(pairData);

            // Логируем статистику пиксельного спреда
            double avgSpread = pixelSpreadService.getAveragePixelSpread(pairData);
            double maxSpread = pixelSpreadService.getMaxPixelSpread(pairData);
            double currentSpread = pixelSpreadService.getCurrentPixelSpread(pairData);

            log.debug("🔢 Пиксельный спред для {}/{}: avg={}px, max={}px, current={}px",
                    pairData.getLongTicker(), pairData.getShortTicker(),
                    String.format("%.1f", avgSpread), String.format("%.1f", maxSpread),
                    String.format("%.1f", currentSpread));

        } catch (Exception e) {
            log.warn("⚠️ Ошибка расчета пиксельного спреда для {}/{}: {}",
                    pairData.getLongTicker(), pairData.getShortTicker(), e.getMessage());
        }

        return pairData;
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
