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

        Settings settingsFromDb = settingsService.getSettingsFromDb();
        List<String> applicableTickers = candlesService.getApplicableTickers("1D", true);
        Map<String, List<Candle>> candlesMap = candlesService.getCandles(applicableTickers, true);
        validateCandlesLimitAndThrow(candlesMap);

        List<ZScoreData> zScoreDataList = PythonRestClient.fetchZScoreData(
                settingsFromDb,
                candlesMap
        );

        // Обработка результатов
        zScoreService.reduceDuplicates(zScoreDataList);
        zScoreService.sortByLongTicker(zScoreDataList);
        zScoreService.sortParamsByTimestampV2(zScoreDataList);
        List<ZScoreData> topN = zScoreService.obtainTopNBestPairs(settingsFromDb, zScoreDataList, (int) settingsFromDb.getUsePairs());

        List<PairData> pairDataList = pairDataService.createPairDataList(topN, candlesMap);
        pairDataList.forEach(pairDataService::saveToDb);
        return pairDataList;
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap) {
        validateService.validateCandlesLimitAndThrow(candlesMap);
    }
}
