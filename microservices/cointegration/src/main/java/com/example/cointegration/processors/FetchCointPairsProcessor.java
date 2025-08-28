package com.example.cointegration.processors;

import com.example.cointegration.client.CandlesFeignClient;
import com.example.cointegration.messaging.SendEventService;
import com.example.cointegration.repositories.TradingPairRepository;
import com.example.cointegration.service.CointPairService;
import com.example.cointegration.service.SettingsService;
import com.example.cointegration.service.ZScoreService;
import com.example.shared.dto.CandlesRequest;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.models.*;
import com.example.shared.utils.NumberFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchCointPairsProcessor {
    private final CointPairService cointPairService;
    private final TradingPairRepository tradingPairRepository;
    private final ZScoreService zScoreService;
    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;
    private final SendEventService sendEventService;

    public List<CointPair> fetchCointPairs(FetchPairsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("❌ FetchPairsRequest не может быть null");
        }

        long start = System.currentTimeMillis();
        log.info("");
        log.info("🔎 Начало поиска пар...");

        Settings settings = settingsService.getSettings();
        List<String> usedTickers = getUsedTickers();
        Map<String, List<Candle>> candlesMap = getCandles(settings, Collections.emptyList());

        if (candlesMap.isEmpty()) {
            log.warn("⚠️ Данные свечей не получены — пропуск поиска.");
            return Collections.emptyList();
        }

        //todo вынести в мс и брать из бд?
        List<ZScoreData> zScoreDataList = computeZScoreData(settings, candlesMap, 1000);
        if (zScoreDataList.isEmpty()) {
            return Collections.emptyList();
        }

        logZScoreResults(zScoreDataList);

        List<CointPair> pairs = createCointPairs(zScoreDataList, candlesMap);

        log.info("✅ Создано {} пар", pairs.size());
        pairs.forEach(p -> log.debug("📈 {}", p.getPairName()));

        log.info("🕒 Время выполнения: {} сек", String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));

        return pairs;
    }

    private List<String> getUsedTickers() {
        List<TradingPair> activePairs = tradingPairRepository.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        List<String> tickers = new ArrayList<>();
        for (TradingPair pair : activePairs) {
            tickers.add(pair.getLongTicker());
            tickers.add(pair.getShortTicker());
        }
        return tickers;
    }

    private Map<String, List<Candle>> getCandles(Settings settings, List<String> tradingTickers) {
        long start = System.currentTimeMillis();
        CandlesRequest request = new CandlesRequest(settings, tradingTickers);
        Map<String, List<Candle>> map = candlesFeignClient.getApplicableCandlesMap(request);
        log.debug("✅ Свечи загружены за {} сек", String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));
        return map;
    }

    private List<ZScoreData> computeZScoreData(Settings settings, Map<String, List<Candle>> candlesMap, int count) {
        try {
            return zScoreService.getTopNZScoreData(settings, candlesMap, count);
        } catch (Exception e) {
            log.error("❌ Ошибка при расчете Z-Score: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void logZScoreResults(List<ZScoreData> dataList) {
        int index = 1;
        for (ZScoreData data : dataList) {
            // Use NumberFormatter.format which handles nulls and returns "N/A"
            String cointegrationPValue = NumberFormatter.format(data.getJohansenCointPValue(), 5);
            String avgAdfPValue = NumberFormatter.format(data.getAvgAdfPvalue(), 5);
            String latestZscore = NumberFormatter.format(data.getLatestZScore(), 2);
            String correlation = NumberFormatter.format(data.getPearsonCorr(), 2);

            log.debug(String.format("%d. Пара: underValuedTicker=%s overValuedTicker=%s | p=%s | adf=%s | z=%s | corr=%s",
                    index++, data.getUnderValuedTicker(), data.getOverValuedTicker(),
                    cointegrationPValue, avgAdfPValue, latestZscore, correlation));
        }
    }

    private List<CointPair> createCointPairs(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        try {
            cointPairService.deleteAllByStatus(TradeStatus.SELECTED);
            return cointPairService.createCointPairList(zScoreDataList, candlesMap);
        } catch (Exception e) {
            log.error("❌ Ошибка при создании PairData: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
