package com.example.core.services;

import com.example.core.repositories.PairRepository;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ChangesData;
import com.example.shared.dto.TradeResult;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.PairType;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingPairService {
    private final PairRepository pairRepository;
    private final CalculateChangesService calculateChangesServiceImpl;
    private final EntryPointService entryPointService;
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final ExcludeExistingTradingPairsService excludeExistingTradingPairsService;
    private final UpdateChangesService updateChangesService;
    private final CreatePairDataService createPairDataService;
    private final CalculateUnrealizedProfitTotalService calculateUnrealizedProfitTotalService;
    private final PortfolioService portfolioServiceImpl;
    private final UpdateSettingsParamService updateSettingsParamService;

    public List<Pair> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<Pair> pairs = createPairDataService.createPairs(top, candlesMap);
        // Сохраняем с обработкой конфликтов
        List<Pair> savedPairs = new ArrayList<>();
        for (Pair pair : pairs) {
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

    public void updateZScoreDataCurrent(Pair tradingPair, ZScoreData zScoreData) {
        updateZScoreDataCurrentService.updateCurrent(tradingPair, zScoreData);
    }

    public void save(Pair tradingPair) {
        tradingPair.setUpdatedTime(LocalDateTime.now()); //перед сохранением обновляем время
        pairRepository.save(tradingPair);
    }

    public void saveAll(List<Pair> tradingPairList) {
        tradingPairList.forEach(pairData -> pairData.setUpdatedTime(LocalDateTime.now()));
        pairRepository.saveAll(tradingPairList);
    }

    public Pair findById(Long id) {
        return pairRepository.findById(id).orElse(null);
    }

    public List<Pair> findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus status) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return pairRepository.findTradingPairsByStatusAndEntryTimeAfter(status, startOfDay);
    }

    public List<Pair> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return pairRepository.findTradingPairsByStatus(status);
    }

    public List<Pair> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return pairRepository.findTradingPairsByStatus(status);
    }

    public List<Pair> findAllByStatusIn(List<TradeStatus> statuses) {
        List<Pair> result = new ArrayList<>();
        for (TradeStatus status : statuses) {
            result.addAll(pairRepository.findTradingPairsByStatus(status));
        }
        return result;
    }

    public List<Pair> findByTickers(String longTicker, String shortTicker) {
        return pairRepository.findByTickerAAndTickerB(longTicker, shortTicker)
                .stream()
                .filter(pair -> pair.getType() == PairType.TRADING)
                .toList();
    }

    @Transactional
    public int deleteAllByStatus(TradeStatus status) {
        return pairRepository.deleteByTypeAndStatus(PairType.TRADING, status);
    }

    public void delete(Pair tradingPair) {
        pairRepository.delete(tradingPair);
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

    public void addEntryPoints(Pair tradingPair, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        entryPointService.addEntryPoints(tradingPair, zScoreData, openLongTradeResult, openShortTradeResult);
    }

    public void addChanges(Pair tradingPair) {
        ChangesData changes = calculateChangesServiceImpl.getChanges(tradingPair);
        updateChangesService.update(tradingPair, changes);
    }

    public void updatePortfolioBalanceBeforeTradeUSDT(Pair tradingPair) {
        portfolioServiceImpl.updatePortfolioBalanceBeforeTradeUSDT(tradingPair);
    }

    public void updatePortfolioBalanceAfterTradeUSDT(Pair tradingPair) {
        portfolioServiceImpl.updatePortfolioBalanceAfterTradeUSDT(tradingPair);
    }

    public void updateSettingsParam(Pair tradingPair, Settings settings) {
        updateSettingsParamService.updateSettingsParam(tradingPair, settings);
    }
}
