package com.example.core.services;

import com.example.core.repositories.TradingPairRepository;
import com.example.shared.dto.ChangesData;
import com.example.shared.dto.ZScoreData;
import com.example.shared.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingPairService {
    private final TradingPairRepository tradingPairRepository;
    private final CalculateChangesService calculateChangesServiceImpl;
    private final EntryPointService entryPointService;
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final ExcludeExistingTradingPairsService excludeExistingTradingPairsService;
    private final UpdateChangesService updateChangesService;
    private final CreatePairDataService createPairDataService;
    private final CalculateUnrealizedProfitTotalService calculateUnrealizedProfitTotalService;
    private final PortfolioService portfolioServiceImpl;
    private final UpdateSettingsParamService updateSettingsParamService;

    public List<TradingPair> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<TradingPair> pairs = createPairDataService.createPairs(top, candlesMap);
        // Сохраняем с обработкой конфликтов
        List<TradingPair> savedPairs = new ArrayList<>();
        for (TradingPair pair : pairs) {
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

    public void updateZScoreDataCurrent(TradingPair tradingPair, ZScoreData zScoreData) {
        updateZScoreDataCurrentService.updateCurrent(tradingPair, zScoreData);
    }

    public void save(TradingPair tradingPair) {
        tradingPair.setUpdatedTime(System.currentTimeMillis()); //перед сохранением обновляем время
        tradingPairRepository.save(tradingPair);
    }

    public void saveAll(List<TradingPair> tradingPairList) {
        tradingPairList.forEach(pairData -> pairData.setUpdatedTime(System.currentTimeMillis()));
        tradingPairRepository.saveAll(tradingPairList);
    }

    public TradingPair findById(Long id) {
        return tradingPairRepository.findById(id).orElse(null);
    }

    public List<TradingPair> findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus status) {
        long startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return tradingPairRepository.findAllByStatusOrderByEntryTimeTodayDesc(status, startOfDay);
    }

    public List<TradingPair> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return tradingPairRepository.findAllByStatusOrderByEntryTimeDesc(status);
    }

    public List<TradingPair> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return tradingPairRepository.findAllByStatusOrderByUpdatedTimeDesc(status);
    }

    public List<TradingPair> findAllByStatusIn(List<TradeStatus> statuses) {
        return tradingPairRepository.findAllByStatusIn(statuses);
    }

    public List<TradingPair> findByTickers(String longTicker, String shortTicker) {
        return tradingPairRepository.findByLongTickerAndShortTicker(longTicker, shortTicker);
    }

    public int deleteAllByStatus(TradeStatus status) {
        return tradingPairRepository.deleteAllByStatus(status);
    }

    public void delete(TradingPair tradingPair) {
        tradingPairRepository.delete(tradingPair);
    }

    public void excludeExistingTradingPairs(List<ZScoreData> zScoreDataList) {
        excludeExistingTradingPairsService.exclude(zScoreDataList);
    }

    public BigDecimal getUnrealizedProfitPercentTotal() {
        return calculateUnrealizedProfitTotalService.getUnrealizedProfitPercentTotal();
    }

    public BigDecimal getUnrealizedProfitUSDTTotal() {
        return calculateUnrealizedProfitTotalService.getUnrealizedProfitUSDTTotal();
    }

    public void addEntryPoints(TradingPair tradingPair, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        entryPointService.addEntryPoints(tradingPair, zScoreData, openLongTradeResult, openShortTradeResult);
    }

    public void addChanges(TradingPair tradingPair) {
        ChangesData changes = calculateChangesServiceImpl.getChanges(tradingPair);
        updateChangesService.update(tradingPair, changes);
    }

    public void updatePortfolioBalanceBeforeTradeUSDT(TradingPair tradingPair) {
        portfolioServiceImpl.updatePortfolioBalanceBeforeTradeUSDT(tradingPair);
    }

    public void updatePortfolioBalanceAfterTradeUSDT(TradingPair tradingPair) {
        portfolioServiceImpl.updatePortfolioBalanceAfterTradeUSDT(tradingPair);
    }

    public void updateSettingsParam(TradingPair tradingPair, Settings settings) {
        updateSettingsParamService.updateSettingsParam(tradingPair, settings);
    }
}
