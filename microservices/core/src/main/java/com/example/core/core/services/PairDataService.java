package com.example.core.core.services;

import com.example.core.core.repositories.PairDataRepository;
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
public class PairDataService {
    private final PairDataRepository pairDataRepository;
    private final CalculateChangesService calculateChangesServiceImpl;
    private final EntryPointService entryPointService;
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final ExcludeExistingTradingPairsService excludeExistingTradingPairsService;
    private final UpdateChangesService updateChangesService;
    private final CreatePairDataService createPairDataService;
    private final CalculateUnrealizedProfitTotalService calculateUnrealizedProfitTotalService;
    private final PortfolioService portfolioServiceImpl;
    private final UpdateSettingsParamService updateSettingsParamService;

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

        log.debug("✅ Успешно сохранено {}/{} пар", savedPairs.size(), pairs.size());

        return pairs;
    }

    public void updateZScoreDataCurrent(PairData pairData, ZScoreData zScoreData) {
        updateZScoreDataCurrentService.updateCurrent(pairData, zScoreData);
    }

    public void save(PairData pairData) {
        pairData.setUpdatedTime(System.currentTimeMillis()); //перед сохранением обновляем время
        pairDataRepository.save(pairData);
    }

    public void saveAll(List<PairData> pairDataList) {
        pairDataList.forEach(pairData -> pairData.setUpdatedTime(System.currentTimeMillis()));
        pairDataRepository.saveAll(pairDataList);
    }

    public PairData findById(Long id) {
        return pairDataRepository.findById(id).orElse(null);
    }

    public List<PairData> findAllByStatusOrderByEntryTimeTodayDesc(TradeStatus status) {
        long startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return pairDataRepository.findAllByStatusOrderByEntryTimeTodayDesc(status, startOfDay);
    }

    public List<PairData> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByEntryTimeDesc(status);
    }

    public List<PairData> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByUpdatedTimeDesc(status);
    }

    public List<PairData> findAllByStatusIn(List<TradeStatus> statuses) {
        return pairDataRepository.findAllByStatusIn(statuses);
    }

    public List<PairData> findByTickers(String longTicker, String shortTicker) {
        return pairDataRepository.findByLongTickerAndShortTicker(longTicker, shortTicker);
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
        ChangesData changes = calculateChangesServiceImpl.getChanges(pairData);
        updateChangesService.update(pairData, changes);
    }

    public void updatePortfolioBalanceBeforeTradeUSDT(PairData pairData) {
        portfolioServiceImpl.updatePortfolioBalanceBeforeTradeUSDT(pairData);
    }

    public void updatePortfolioBalanceAfterTradeUSDT(PairData pairData) {
        portfolioServiceImpl.updatePortfolioBalanceAfterTradeUSDT(pairData);
    }

    public void updateSettingsParam(PairData pairData, Settings settings) {
        updateSettingsParamService.updateSettingsParam(pairData, settings);
    }
}
