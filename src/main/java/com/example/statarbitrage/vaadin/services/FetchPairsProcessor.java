package com.example.statarbitrage.vaadin.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.services.*;
import com.example.statarbitrage.threecommas.ThreeCommasFlowService;
import com.example.statarbitrage.threecommas.ThreeCommasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchPairsProcessor {
    private final PairDataService pairDataService;
    private final ChartService chartService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final FileService fileService;
    private final SettingsService settingsService;
    private final CsvLogService csvLogService;
    private final ValidateService validateService;
    private final ThreeCommasService threeCommasService;
    private final ThreeCommasFlowService threeCommasFlowService;
    private final EventSendService eventSendService;
    private final TradeLogService tradeLogService;
    private final ExportService exportService;
    private final CointegrationCalculator cointegrationCalculator;

    public List<PairData> fetchPairs() {
        log.info("Fetching pairs...");

        List<String> applicableTickers = candlesService.getApplicableTickers("1D", true);
        Map<String, List<Candle>> candlesMap = candlesService.getCandles(applicableTickers, true);
        validateCandlesLimitAndThrow(candlesMap);

        // 2. Создаем пул потоков
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            // 3. Параллельный расчет коинтеграции
            List<CompletableFuture<ZScoreData>> futures = new ArrayList<>();

            // Генерируем все возможные пары
            for (int i = 0; i < applicableTickers.size(); i++) {
                for (int j = i + 1; j < applicableTickers.size(); j++) {
                    String ticker1 = applicableTickers.get(i);
                    String ticker2 = applicableTickers.get(j);

                    CompletableFuture<ZScoreData> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            double[] prices1 = getClosePrices(candlesMap.get(ticker1));
                            double[] prices2 = getClosePrices(candlesMap.get(ticker2));
                            return cointegrationCalculator.calculatePairZScores(
                                    settingsService.getSettingsFromDb(),
                                    ticker1, ticker2,
                                    prices1, prices2
                            );
                        } catch (Exception e) {
                            log.error("Error processing pair {}/{}: {}", ticker1, ticker2, e.getMessage());
                            return null;
                        }
                    }, executor);

                    futures.add(future);
                }
            }

            // 4. Собираем результаты
            List<ZScoreData> zScoreDataList = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 5. Обработка результатов
            zScoreService.reduceDuplicates(zScoreDataList);
            zScoreService.sortByLongTicker(zScoreDataList);
            zScoreService.sortParamsByTimestampV2(zScoreDataList);
            List<ZScoreData> top10 = zScoreService.obtainTop10(zScoreDataList);

            List<PairData> pairDataList = pairDataService.createPairDataList(top10, candlesMap);
            pairDataList.forEach(pairDataService::saveToDb);
            return pairDataList;
        } finally {
            executor.shutdown();
        }
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap) {
        validateService.validateCandlesLimitAndThrow(candlesMap);
    }

    public double[] getClosePrices(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return new double[0];
        }
        return candles.stream()
                .mapToDouble(Candle::getClose)
                .toArray();
    }
}
