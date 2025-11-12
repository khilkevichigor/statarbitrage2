//package com.example.cointegration.service;
//
//import com.example.shared.dto.Candle;
//import com.example.shared.dto.ZScoreData;
//import com.example.shared.enums.TradeStatus;
//import com.example.shared.models.Pair;
//import com.example.shared.enums.PairType;
//import com.example.shared.utils.CandlesUtil;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class CreateCointPairService {
//
//    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
//    private final PixelSpreadService pixelSpreadService;
//
//    /**
//     * Создаёт список торговых пар PairData на основе списка Z-оценок и данных свечей
//     */
//    public List<Pair> createCointPairs(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
//        List<Pair> result = new ArrayList<>();
//
//        for (ZScoreData zScoreData : zScoreDataList) {
//            try {
//                Pair cointPair = buildCointPair(zScoreData, candlesMap);
//                result.add(cointPair);
//            } catch (IllegalArgumentException e) {
//                log.warn("⚠️ Пропущена пара {}/{}: {}",
//                        zScoreData.getUnderValuedTicker(),
//                        zScoreData.getOverValuedTicker(),
//                        e.getMessage());
//            } catch (Exception e) {
//                log.error("❌ Ошибка при создании PairData для пары {}/{}: {}",
//                        zScoreData.getUnderValuedTicker(),
//                        zScoreData.getOverValuedTicker(),
//                        e.getMessage(), e);
//            }
//        }
//
//        return result;
//    }
//
//    /**
//     * Строит одну пару на основе Z-данных и свечей
//     */
//    private Pair buildCointPair(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
//        String undervalued = zScoreData.getUnderValuedTicker();
//        String overvalued = zScoreData.getOverValuedTicker();
//
//        List<Candle> undervaluedCandles = candlesMap.get(undervalued);
//        List<Candle> overvaluedCandles = candlesMap.get(overvalued);
//
//        if (isEmpty(undervaluedCandles) || isEmpty(overvaluedCandles)) {
//            throw new IllegalArgumentException("Недостаточно данных по свечам");
//        }
//
//        Pair cointPair = Pair.builder()
//                .type(PairType.COINTEGRATED)
//                .tickerA(undervalued)
//                .tickerB(overvalued)
//                .pairName(undervalued + "/" + overvalued)
//                .status(TradeStatus.SELECTED)
//                .longTickerCurrentPrice(java.math.BigDecimal.valueOf(CandlesUtil.getLastClose(undervaluedCandles)))
//                .shortTickerCurrentPrice(java.math.BigDecimal.valueOf(CandlesUtil.getLastClose(overvaluedCandles)))
//                .longTickerCandles(undervaluedCandles)
//                .shortTickerCandles(overvaluedCandles)
//                .timestamp(System.currentTimeMillis())
//                .createdAt(java.time.LocalDateTime.now())
//                .updatedTime(java.time.LocalDateTime.now())
//                .build();
//
//        updateZScoreDataCurrentService.updateCurrent(cointPair, zScoreData);
//
//        // Рассчитываем пиксельный спред для новой пары
//        try {
//            pixelSpreadService.calculatePixelSpreadIfNeeded(cointPair);
//
//            // Логируем статистику пиксельного спреда
//            double avgSpread = pixelSpreadService.getAveragePixelSpread(cointPair);
//            double maxSpread = pixelSpreadService.getMaxPixelSpread(cointPair);
//            double currentSpread = pixelSpreadService.getCurrentPixelSpread(cointPair);
//
//            log.debug("🔢 Пиксельный спред для {}/{}: avg={}px, max={}px, current={}px",
//                    cointPair.getLongTicker(), cointPair.getShortTicker(),
//                    String.format("%.1f", avgSpread), String.format("%.1f", maxSpread),
//                    String.format("%.1f", currentSpread));
//
//        } catch (Exception e) {
//            log.warn("⚠️ Ошибка расчета пиксельного спреда для {}/{}: {}",
//                    cointPair.getLongTicker(), cointPair.getShortTicker(), e.getMessage());
//        }
//
//        return cointPair;
//    }
//
//    private boolean isEmpty(List<?> list) {
//        return list == null || list.isEmpty();
//    }
//}
