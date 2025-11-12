//package com.example.cointegration.service;
//
//import com.example.cointegration.repositories.PairRepository;
//import com.example.shared.dto.Candle;
//import com.example.shared.dto.ZScoreData;
//import com.example.shared.enums.TradeStatus;
//import com.example.shared.models.Pair;
//import com.example.shared.enums.PairType;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDate;
//import java.time.ZoneId;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class CointPairService {
//    private final PairRepository cointPairRepository;
//    //    private final CalculateChangesService calculateChangesServiceImpl;
////    private final EntryPointService entryPointService;
//    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
//    private final ExcludeExistingTradingPairsService excludeExistingTradingPairsService;
//    //    private final UpdateChangesService updateChangesService;
//    private final CreateCointPairService createCointPairService;
////    private final CalculateUnrealizedProfitTotalService calculateUnrealizedProfitTotalService;
////    private final PortfolioService portfolioServiceImpl;
////    private final UpdateSettingsParamService updateSettingsParamService;
//
//    public List<Pair> createCointPairList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
//        List<Pair> pairs = createCointPairService.createCointPairs(top, candlesMap);
//        // Сохраняем с обработкой конфликтов
//        List<Pair> savedPairs = new ArrayList<>();
//        for (Pair pair : pairs) {
//            try {
//                save(pair);
//                savedPairs.add(pair);
//            } catch (RuntimeException e) {
//                log.warn("⚠️ Не удалось сохранить пару {}/{}: {} - пропускаем",
//                        pair.getTickerA(), pair.getTickerB(), e.getMessage());
//                // Продолжаем обработку остальных пар
//            }
//        }
//
//        log.debug("✅ Успешно сохранено {}/{} пар", savedPairs.size(), pairs.size());
//
//        return pairs;
//    }
//
//    public void updateZScoreDataCurrent(Pair cointPair, ZScoreData zScoreData) {
//        updateZScoreDataCurrentService.updateCurrent(cointPair, zScoreData);
//    }
//
//    public void save(Pair cointPair) {
//        cointPair.setUpdatedTime(java.time.LocalDateTime.now()); // перед сохранением обновляем время
//        cointPairRepository.save(cointPair);
//    }
//
//    public void saveAll(List<Pair> cointPairList) {
//        cointPairList.forEach(v -> v.setUpdatedTime(java.time.LocalDateTime.now()));
//        cointPairRepository.saveAll(cointPairList);
//    }
//
//    public Pair findById(Long id) {
//        return cointPairRepository.findById(id).orElse(null);
//    }
//
//    public List<Pair> findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus status) {
//        long startOfDay = LocalDate.now()
//                .atStartOfDay(ZoneId.systemDefault())
//                .toInstant()
//                .toEpochMilli();
//        return cointPairRepository.findByTypeAndStatusOrderByCreatedAtDesc(PairType.COINTEGRATED, status);
//    }
//
//    public List<Pair> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
//        return cointPairRepository.findByTypeAndStatusOrderByCreatedAtDesc(PairType.COINTEGRATED, status);
//    }
//
//    public List<Pair> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
//        return cointPairRepository.findByTypeAndStatusOrderByCreatedAtDesc(PairType.COINTEGRATED, status);
//    }
//
//    public List<Pair> findAllByStatusIn(List<TradeStatus> statuses) {
//        return cointPairRepository.findByTypeOrderByCreatedAtDesc(PairType.COINTEGRATED).stream()
//                .filter(p -> statuses.contains(p.getStatus()))
//                .collect(java.util.stream.Collectors.toList());
//    }
//
//    public List<Pair> findByTickers(String longTicker, String shortTicker) {
//        return cointPairRepository.findByTickerAAndTickerB(longTicker, shortTicker);
//    }
//
//    public int deleteAllByStatus(TradeStatus status) {
//        return cointPairRepository.deleteByTypeAndStatus(PairType.COINTEGRATED, status);
//    }
//
//    public void delete(Pair cointPair) {
//        cointPairRepository.delete(cointPair);
//    }
//
//    public void excludeExistingTradingPairs(List<ZScoreData> zScoreDataList) {
//        excludeExistingTradingPairsService.exclude(zScoreDataList);
//    }
//
////    public BigDecimal getUnrealizedProfitPercentTotal() {
////        return calculateUnrealizedProfitTotalService.getUnrealizedProfitPercentTotal();
////    }
//
////    public BigDecimal getUnrealizedProfitUSDTTotal() {
////        return calculateUnrealizedProfitTotalService.getUnrealizedProfitUSDTTotal();
////    }
//
////    public void addEntryPoints(CointPair cointPair, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
////        entryPointService.addEntryPoints(cointPair, zScoreData, openLongTradeResult, openShortTradeResult);
////    }
//
////    public void addChanges(TradingPair tradingPair) {
////        ChangesData changes = calculateChangesServiceImpl.getChanges(tradingPair);
////        updateChangesService.update(tradingPair, changes);
////    }
//
////    public void updatePortfolioBalanceBeforeTradeUSDT(TradingPair tradingPair) {
////        portfolioServiceImpl.updatePortfolioBalanceBeforeTradeUSDT(tradingPair);
////    }
////
////    public void updatePortfolioBalanceAfterTradeUSDT(TradingPair tradingPair) {
////        portfolioServiceImpl.updatePortfolioBalanceAfterTradeUSDT(tradingPair);
////    }
////
////    public void updateSettingsParam(TradingPair tradingPair, Settings settings) {
////        updateSettingsParamService.updateSettingsParam(tradingPair, settings);
////    }
//}
