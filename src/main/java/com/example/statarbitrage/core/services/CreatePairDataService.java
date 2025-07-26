package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.CandlesUtil;
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
                        zScoreData.getUndervaluedTicker(),
                        zScoreData.getOvervaluedTicker(),
                        e.getMessage());
            } catch (Exception e) {
                log.error("❌ Ошибка при создании PairData для пары {}/{}: {}",
                        zScoreData.getUndervaluedTicker(),
                        zScoreData.getOvervaluedTicker(),
                        e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * Строит одну пару на основе Z-данных и свечей
     */
    private PairData buildPairData(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
        String undervalued = zScoreData.getUndervaluedTicker();
        String overvalued = zScoreData.getOvervaluedTicker();

        List<Candle> undervaluedCandles = candlesMap.get(undervalued);
        List<Candle> overvaluedCandles = candlesMap.get(overvalued);

        if (isEmpty(undervaluedCandles) || isEmpty(overvaluedCandles)) {
            throw new IllegalArgumentException("Недостаточно данных по свечам");
        }

        PairData pairData = new PairData(undervalued, overvalued);
        pairData.setStatus(TradeStatus.SELECTED);
        pairData.setLongTickerCurrentPrice(CandlesUtil.getLastClose(undervaluedCandles));
        pairData.setShortTickerCurrentPrice(CandlesUtil.getLastClose(overvaluedCandles));

        updateZScoreDataCurrentService.updateCurrent(pairData, zScoreData);

        return pairData;
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
