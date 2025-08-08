package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import com.example.statarbitrage.trading.model.TradeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final PairDataRepository pairDataRepository;
    private final CalculateChangesService calculateChangesService;
    private final EntryPointService entryPointService;
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final ExcludeExistingTradingPairsService excludeExistingTradingPairsService;
    private final UpdateChangesService updateChangesService;
    private final CreatePairDataService createPairDataService;
    private final CalculateUnrealizedProfitTotalService calculateUnrealizedProfitTotalService;
    private final PortfolioService portfolioService;

    public List<PairData> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<PairData> pairs = createPairDataService.createPairs(top, candlesMap);
        // Сохраняем с обработкой конфликтов
        List<PairData> savedPairs = new ArrayList<>();
        for (PairData pair : pairs) {
            try {
                save(pair);
                savedPairs.add(pair);
            } catch (RuntimeException e) {
                log.warn("⚠️ Не удалось сохранить пару {}/{}: {} - пропускаем",
                        pair.getLongTicker(), pair.getShortTicker(), e.getMessage());
                // Продолжаем обработку остальных пар
            }
        }

        log.info("✅ Успешно сохранено {}/{} пар", savedPairs.size(), pairs.size());

        return pairs;
    }

    public void updateZScoreDataCurrent(PairData pairData, ZScoreData zScoreData) {
        updateZScoreDataCurrentService.updateCurrent(pairData, zScoreData);
    }

    public void save(PairData pairData) {
        pairData.setUpdatedTime(System.currentTimeMillis()); //перед сохранением обновляем время
        pairDataRepository.save(pairData);
    }

    public PairData findById(Long id) {
        return pairDataRepository.findById(id).orElse(null);
    }

    public List<PairData> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByEntryTimeDesc(status);
    }

    public List<PairData> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByUpdatedTimeDesc(status);
    }

    public int deleteAllByStatus(TradeStatus status) {
        return pairDataRepository.deleteAllByStatus(status);
    }

    public void delete(PairData pairData) {
        pairDataRepository.delete(pairData);
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

    public void addEntryPoints(PairData pairData, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        entryPointService.addEntryPoints(pairData, zScoreData, openLongTradeResult, openShortTradeResult);
    }

    public void addChanges(PairData pairData) {
        ChangesData changes = calculateChangesService.getChanges(pairData);
        updateChangesService.update(pairData, changes);
    }

    public void updatePortfolioBalanceBeforeTradeUSDT(PairData pairData) {
        portfolioService.updatePortfolioBalanceBeforeTradeUSDT(pairData);
    }

    public void updatePortfolioBalanceAfterTradeUSDT(PairData pairData) {
        portfolioService.updatePortfolioBalanceAfterTradeUSDT(pairData);
    }
}
