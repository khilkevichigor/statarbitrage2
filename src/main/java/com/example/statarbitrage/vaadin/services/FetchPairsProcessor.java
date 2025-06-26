package com.example.statarbitrage.vaadin.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.services.*;
import com.example.statarbitrage.threecommas.ThreeCommasFlowService;
import com.example.statarbitrage.threecommas.ThreeCommasService;
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
    private final CointegrationCalculatorV2 cointegrationCalculatorV2;

    public List<PairData> fetchPairs() {
        log.info("Fetching pairs...");

        Settings settingsFromDb = settingsService.getSettingsFromDb();
        List<String> applicableTickers = candlesService.getApplicableTickers("1D", true);
        Map<String, List<Candle>> candlesMap = candlesService.getCandles(applicableTickers, true);
        validateCandlesLimitAndThrow(candlesMap);

        List<ZScoreData> zScoreDataList = cointegrationCalculatorV2.calculateZScores(settingsFromDb, candlesMap, false);

        // Обработка результатов
        zScoreService.reduceDuplicates(zScoreDataList);
        zScoreService.sortByLongTicker(zScoreDataList);
        zScoreService.sortParamsByTimestampV2(zScoreDataList);
        List<ZScoreData> topN = zScoreService.obtainTopNBestPairs(settingsFromDb, zScoreDataList, 10);

        List<PairData> pairDataList = pairDataService.createPairDataList(topN, candlesMap);
        pairDataList.forEach(pairDataService::saveToDb);
        return pairDataList;
    }

    private void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap) {
        validateService.validateCandlesLimitAndThrow(candlesMap);
    }
}
