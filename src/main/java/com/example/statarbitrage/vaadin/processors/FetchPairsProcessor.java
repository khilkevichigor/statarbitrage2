package com.example.statarbitrage.vaadin.processors;

import com.example.statarbitrage.model.*;
import com.example.statarbitrage.services.CandlesService;
import com.example.statarbitrage.services.PairDataService;
import com.example.statarbitrage.services.SettingsService;
import com.example.statarbitrage.services.ZScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

//todo проверять были ли Z ниже -2 и выше +2

@Slf4j
@Service
@RequiredArgsConstructor
public class FetchPairsProcessor {
    private final PairDataService pairDataService;
    private final ZScoreService zScoreService;
    private final CandlesService candlesService;
    private final SettingsService settingsService;

    public List<PairData> fetchPairs(Integer countOfPairs) {
        Settings settings = settingsService.getSettingsFromDb();
        Map<String, List<Candle>> candlesMap = candlesService.getCandlesMap(settings);
        int count = countOfPairs != null ? countOfPairs : (int) settings.getUsePairs();
        List<ZScoreData> zScoreDataList = zScoreService.getTopNPairs(settings, candlesMap, count);

        for (int i = 0; i < zScoreDataList.size(); i++) {
            ZScoreData pair = zScoreDataList.get(i);
            ZScoreParam latest = pair.getLastZScoreParam();
            log.info(String.format("%d. Пара: long=%s short=%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f", i + 1, pair.getLongTicker(), pair.getShortTicker(), latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()));
        }

        List<PairData> topPairs = pairDataService.createPairDataList(zScoreDataList, candlesMap);
        log.info("Создали {} новых PairData", topPairs.size());
        return topPairs;
    }
}
