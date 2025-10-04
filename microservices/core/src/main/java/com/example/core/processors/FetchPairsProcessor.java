package com.example.core.processors;

import com.example.core.client.CandlesFeignClient;
import com.example.core.services.PairService;
import com.example.core.services.SettingsService;
import com.example.core.services.ZScoreService;
import com.example.shared.dto.Candle;
import com.example.shared.dto.ExtendedCandlesRequest;
import com.example.shared.dto.FetchPairsRequest;
import com.example.shared.dto.ZScoreData;
import com.example.shared.enums.TradeStatus;
import com.example.shared.models.Pair;
import com.example.shared.models.Settings;
import com.example.shared.utils.NumberFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchPairsProcessor {
    private final PairService pairService;
    private final ZScoreService zScoreService;
    private final CandlesFeignClient candlesFeignClient;
    private final SettingsService settingsService;

    public List<Pair> fetchPairs(FetchPairsRequest request) {
        //todo здесь делать вызов в cointegration ms или брать из бд coint_pair либо
        // получать пары от cointegration через receiveEventService и класть их в coint_pair таблицу а уже здесь брать их из этой таблицы проверяя на свежесть

        if (request == null) {
            throw new IllegalArgumentException("❌ FetchPairsRequest не может быть null");
        }

        long start = System.currentTimeMillis();
        log.info("");
        log.info("🔎 Начало поиска пар...");

        Settings settings = settingsService.getSettings();
        List<String> usedTickers = getUsedTickers();
        Map<String, List<Candle>> candlesMap = getCandles(settings, usedTickers);

        if (candlesMap.isEmpty()) {
            log.warn("⚠️ Данные свечей не получены — пропуск поиска.");
            return Collections.emptyList();
        }

        int count = Optional.ofNullable(request.getCountOfPairs())
                .orElse((int) settings.getUsePairs());

        //todo вынести в мс и брать из бд?
        List<ZScoreData> zScoreDataList = computeZScoreData(settings, candlesMap, count);
        if (zScoreDataList.isEmpty()) {
            return Collections.emptyList();
        }

        logZScoreResults(zScoreDataList);

        List<Pair> pairs = createPairs(zScoreDataList, candlesMap);

        log.debug("✅ Создано {} пар", pairs.size());
        pairs.forEach(p -> log.debug("📈 {}", p.getPairName()));
        log.debug("🕒 Время выполнения: {} сек", String.format("%.2f", (System.currentTimeMillis() - start) / 1000.0));

        return pairs;
    }

    private List<String> getUsedTickers() {
        List<Pair> activePairs = pairService.findAllByStatusOrderByEntryTimeDesc(TradeStatus.TRADING);
        List<String> tickers = new ArrayList<>();
        for (Pair pair : activePairs) {
            tickers.add(pair.getLongTicker());
            tickers.add(pair.getShortTicker());
        }
        return tickers;
    }

    private Map<String, List<Candle>> getCandles(Settings settings, List<String> tradingTickers) {
        long start = System.currentTimeMillis();

        log.info("📊 Запрос свечей: таймфрейм={}, лимит={}, исключить_тикеров={}",
                settings.getTimeframe(), (int) settings.getCandleLimit(),
                tradingTickers != null ? tradingTickers.size() : 0);

        List<String> blacklistItems = Arrays.asList(settings.getMinimumLotBlacklist().split(","));
        List<String> excludedTickers = new ArrayList<>();
        excludedTickers.addAll(tradingTickers);
        excludedTickers.addAll(blacklistItems);

        // Создаем ExtendedCandlesRequest для получения свечей через пагинацию
        ExtendedCandlesRequest request = ExtendedCandlesRequest.builder()
                .timeframe(settings.getTimeframe())
                .candleLimit((int) settings.getCandleLimit())
                .minVolume(settings.getMinVolume())
                .tickers(null) // Получаем все доступные тикеры
                .excludeTickers(excludedTickers)
                .period("1 год")
                .build();

        try {
            log.info("⏳ Отправка запроса к candles микросервису...");
            Map<String, List<Candle>> map = candlesFeignClient.getValidatedCacheExtended(request);

            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            if (map != null && !map.isEmpty()) {
                log.info("✅ Свечи загружены за {} сек. Получено {} тикеров",
                        String.format("%.2f", elapsed), map.size());
            } else {
                log.warn("⚠️ Получен пустой результат за {} сек", String.format("%.2f", elapsed));
            }

            return map != null ? map : new HashMap<>();

        } catch (Exception e) {
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            log.error("❌ Ошибка при получении свечей за {} сек: {}",
                    String.format("%.2f", elapsed), e.getMessage());
            return new HashMap<>();
        }
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

    private List<Pair> createPairs(List<ZScoreData> zScoreDataList, Map<String, List<Candle>> candlesMap) {
        try {
            return pairService.createPairDataList(zScoreDataList, candlesMap);
        } catch (Exception e) {
            log.error("❌ Ошибка при создании PairData: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
