package com.example.core.services;

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
    private Pair buildPairData(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
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
        
        // Заполняем поля настроек сразу при создании пары
        Settings settings = settingsService.getSettings();
        tradingPair.setSettingsCandleLimit(BigDecimal.valueOf(settings.getCandleLimit()));
        tradingPair.setSettingsMinZ(BigDecimal.valueOf(settings.getMinZ()));
        
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

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
