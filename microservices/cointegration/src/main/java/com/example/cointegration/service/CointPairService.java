package com.example.cointegration.service;

import com.example.cointegration.repositories.CointPairRepository;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.CointPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CointPairService {
    private final CointPairRepository cointPairRepository;
    //    private final CalculateChangesService calculateChangesServiceImpl;
//    private final EntryPointService entryPointService;
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final ExcludeExistingTradingPairsService excludeExistingTradingPairsService;
    //    private final UpdateChangesService updateChangesService;
    private final CreateCointPairService createCointPairService;
//    private final CalculateUnrealizedProfitTotalService calculateUnrealizedProfitTotalService;
//    private final PortfolioService portfolioServiceImpl;
//    private final UpdateSettingsParamService updateSettingsParamService;

    public List<CointPair> createCointPairList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<CointPair> pairs = createCointPairService.createCointPairs(top, candlesMap);
        // Сохраняем с обработкой конфликтов
        List<CointPair> savedPairs = new ArrayList<>();
        for (CointPair pair : pairs) {
            try {
                save(pair);
                savedPairs.add(pair);
            } catch (RuntimeException e) {
                log.warn("⚠️ Не удалось сохранить пару {}/{}: {} - пропускаем",
                        pair.getLongTicker(), pair.getShortTicker(), e.getMessage());
                // Продолжаем обработку остальных пар
            }
        }

        log.debug("✅ Успешно сохранено {}/{} пар", savedPairs.size(), pairs.size());

        return pairs;
    }

    public void updateZScoreDataCurrent(CointPair cointPair, ZScoreData zScoreData) {
        updateZScoreDataCurrentService.updateCurrent(cointPair, zScoreData);
    }

    public void save(CointPair cointPair) {
        cointPair.setUpdatedTime(System.currentTimeMillis()); //перед сохранением обновляем время
        cointPairRepository.save(cointPair);
    }

    public void saveAll(List<CointPair> cointPairList) {
        cointPairList.forEach(v -> v.setUpdatedTime(System.currentTimeMillis()));
        cointPairRepository.saveAll(cointPairList);
    }

    public CointPair findById(Long id) {
        return cointPairRepository.findById(id).orElse(null);
    }

    public List<CointPair> findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus status) {
        long startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return cointPairRepository.findAllByStatusOrderByEntryTimeTodayDesc(status, startOfDay);
    }

    public List<CointPair> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return cointPairRepository.findAllByStatusOrderByEntryTimeDesc(status);
    }

    public List<CointPair> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return cointPairRepository.findAllByStatusOrderByUpdatedTimeDesc(status);
    }

    public List<CointPair> findAllByStatusIn(List<TradeStatus> statuses) {
        return cointPairRepository.findAllByStatusIn(statuses);
    }

    public List<CointPair> findByTickers(String longTicker, String shortTicker) {
        return cointPairRepository.findByLongTickerAndShortTicker(longTicker, shortTicker);
    }

    public int deleteAllByStatus(TradeStatus status) {
        return cointPairRepository.deleteAllByStatus(status);
    }

    public void delete(CointPair cointPair) {
        cointPairRepository.delete(cointPair);
    }

    public void excludeExistingTradingPairs(List<ZScoreData> zScoreDataList) {
        excludeExistingTradingPairsService.exclude(zScoreDataList);
    }

//    public BigDecimal getUnrealizedProfitPercentTotal() {
//        return calculateUnrealizedProfitTotalService.getUnrealizedProfitPercentTotal();
//    }

//    public BigDecimal getUnrealizedProfitUSDTTotal() {
//        return calculateUnrealizedProfitTotalService.getUnrealizedProfitUSDTTotal();
//    }

//    public void addEntryPoints(CointPair cointPair, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
//        entryPointService.addEntryPoints(cointPair, zScoreData, openLongTradeResult, openShortTradeResult);
//    }

//    public void addChanges(TradingPair tradingPair) {
//        ChangesData changes = calculateChangesServiceImpl.getChanges(tradingPair);
//        updateChangesService.update(tradingPair, changes);
//    }

//    public void updatePortfolioBalanceBeforeTradeUSDT(TradingPair tradingPair) {
//        portfolioServiceImpl.updatePortfolioBalanceBeforeTradeUSDT(tradingPair);
//    }
//
//    public void updatePortfolioBalanceAfterTradeUSDT(TradingPair tradingPair) {
//        portfolioServiceImpl.updatePortfolioBalanceAfterTradeUSDT(tradingPair);
//    }
//
//    public void updateSettingsParam(TradingPair tradingPair, Settings settings) {
//        updateSettingsParamService.updateSettingsParam(tradingPair, settings);
//    }
}
