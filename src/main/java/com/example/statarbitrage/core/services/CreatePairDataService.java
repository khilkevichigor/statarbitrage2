package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.CandlesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePairDataService {
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;

    public PairData createPair(ZScoreData zScoreData, List<Candle> underValuedTickerCandles, List<Candle> overValuedTickerCandles) {
        // Проверяем наличие данных
        if (underValuedTickerCandles == null || underValuedTickerCandles.isEmpty() || overValuedTickerCandles == null || overValuedTickerCandles.isEmpty()) {
            log.warn("⚠️ Нет данных по свечам для пары: {} - {}", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker());
            throw new IllegalArgumentException("⚠️ Отсутствуют данные свечей");
        }

        PairData pairData = new PairData();

        pairData.setStatus(TradeStatus.SELECTED);

        // Устанавливаем основные параметры
        pairData.setLongTicker(zScoreData.getUndervaluedTicker());
        pairData.setShortTicker(zScoreData.getOvervaluedTicker());

        // Устанавливаем текущие цены
        pairData.setLongTickerCurrentPrice(CandlesUtil.getLastClose(underValuedTickerCandles));
        pairData.setShortTickerCurrentPrice(CandlesUtil.getLastClose(overValuedTickerCandles));

        updateZScoreDataCurrentService.updateCurrent(pairData, zScoreData);

        return pairData;
    }
}
