package com.example.statarbitrage.vaadin.processors;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.services.*;
import com.example.statarbitrage.vaadin.python.PythonRestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchPairsProcessor {
    private final PairDataService pairDataService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;
    private final ValidateService validateService;

    public List<PairData> fetchPairs() {
        log.info("Fetching pairs...");

        Settings settings = settingsService.getSettingsFromDb();
        List<String> applicableTickers = candlesService.getApplicableTickers("1D", true);
        Map<String, List<Candle>> candlesMap = candlesService.getCandles(applicableTickers, true);
        validateService.validateCandlesLimitAndThrow(candlesMap);

        List<ZScoreData> zScoreDataList = PythonRestClient.fetchZScoreData(
                settings,
                candlesMap
        );

        // Обработка результатов
        zScoreService.reduceDuplicates(zScoreDataList);

        zScoreService.handleNegativeZ(zScoreDataList);
        validateService.validatePositiveZ(zScoreDataList);

        pairDataService.excludeExistingTradingPairs(zScoreDataList);

        zScoreService.sortByLongTicker(zScoreDataList);

        zScoreService.sortParamsByTimestampV2(zScoreDataList);

        List<ZScoreData> topZScoreData = zScoreService.obtainTopNBestPairs(settings, zScoreDataList, (int) settings.getUsePairs());

        List<PairData> topPairData = pairDataService.createPairDataList(topZScoreData, candlesMap);
        topPairData.forEach(pairDataService::saveToDb);
        return topPairData;
    }
}
