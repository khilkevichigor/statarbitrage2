package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ChangesData;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.TradeStatus;
import com.example.statarbitrage.common.utils.CandlesUtil;
import com.example.statarbitrage.core.repositories.PairDataRepository;
import com.example.statarbitrage.trading.model.TradeResult;
import com.example.statarbitrage.trading.services.TradingIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final PairDataRepository pairDataRepository;
    private final TradingIntegrationService tradingIntegrationService;
    private final CalculateChangesService calculateChangesService;
    private final EntryPointService entryPointService;
    private final UpdateZScoreDataCurrentService updateZScoreDataCurrentService;
    private final ExcludeExistingTradingPairsService excludeExistingTradingPairsService;
    private final UpdateChangesService updateChangesService;
    private final CreatePairDataService createPairDataService;

    public List<PairData> createPairDataList(List<ZScoreData> top, Map<String, List<Candle>> candlesMap) {
        List<PairData> result = new ArrayList<>();

        for (ZScoreData zScoreData : top) {
            try {
                List<Candle> underValuedTickerCandles = candlesMap.get(zScoreData.getUndervaluedTicker());
                List<Candle> overValuedTickerCandles = candlesMap.get(zScoreData.getOvervaluedTicker());
                PairData newPairData = createPairData(zScoreData, underValuedTickerCandles, overValuedTickerCandles);
                result.add(newPairData);
            } catch (Exception e) {
                log.error("Ошибка при создании PairData для пары {}/{}: {}",
                        zScoreData.getUndervaluedTicker(),
                        zScoreData.getOvervaluedTicker(),
                        e.getMessage());
            }
        }

        // Сохраняем с обработкой конфликтов
        List<PairData> savedPairs = new ArrayList<>();
        for (PairData pair : result) {
            try {
                save(pair);
                savedPairs.add(pair);
            } catch (RuntimeException e) {
                log.warn("⚠️ Не удалось сохранить пару {}/{}: {} - пропускаем",
                        pair.getLongTicker(), pair.getShortTicker(), e.getMessage());
                // Продолжаем обработку остальных пар
            }
        }

        log.info("✅ Успешно сохранено {}/{} пар", savedPairs.size(), result.size());

        return result;
    }

    private PairData createPairData(ZScoreData zScoreData, List<Candle> underValuedTickerCandles, List<Candle> overValuedTickerCandles) {
        return createPairDataService.createPair(zScoreData, underValuedTickerCandles, overValuedTickerCandles);
    }

    public void updateZScoreDataCurrent(PairData pairData, ZScoreData zScoreData) {
        updateZScoreDataCurrentService.updateCurrent(pairData, zScoreData);
    }

    public void save(PairData pairData) {
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

    public BigDecimal getUnrealizedProfitTotal() {
        List<PairData> tradingPairs = findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        return tradingPairs.stream()
                .map(PairData::getProfitPercentChanges)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Deprecated
    public void addCurrentPricesFromCandles(PairData pairData, Map<String, List<Candle>> candlesMap) {
        List<Candle> longTickerCandles = candlesMap.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(pairData.getShortTicker());
        double longTickerCurrentPrice = CandlesUtil.getLastClose(longTickerCandles);
        double shortTickerCurrentPrice = CandlesUtil.getLastClose(shortTickerCandles);
        pairData.setLongTickerCurrentPrice(longTickerCurrentPrice);
        pairData.setShortTickerCurrentPrice(shortTickerCurrentPrice);
    }

    public void addEntryPoints(PairData pairData, ZScoreData zScoreData, TradeResult openLongTradeResult, TradeResult openShortTradeResult) {
        entryPointService.addEntryPoints(pairData, zScoreData, openLongTradeResult, openShortTradeResult);
    }

    public void addChanges(PairData pairData) {
        ChangesData changes = calculateChangesService.getChanges(pairData);
        updateChangesService.update(pairData, changes);
    }
}
