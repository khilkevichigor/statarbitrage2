package com.example.statarbitrage.vaadin.processors;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreData;
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

    //todo можно не переворачивать -Z а просто удалять все -Z тк пары зеркальны и мы оставим тоже самое но с +Z
    public List<PairData> fetchPairs(Integer countOfPairs) {
        long start = System.currentTimeMillis();
        log.info("Fetching pairs...");

        Settings settings = settingsService.getSettingsFromDb();
        Map<String, List<Candle>> candlesMap = candlesService.getCandlesMap(settings);
        int count = countOfPairs != null ? countOfPairs : (int) settings.getUsePairs();
        List<ZScoreData> zScoreDataList = zScoreService.getTopNPairs(settings, candlesMap, count);
        List<PairData> topPairs = pairDataService.createPairDataList(zScoreDataList, candlesMap);

        long end = System.currentTimeMillis();
        log.info("⏱️ fetchPairs() finished in {} сек", (end - start) / 1000.0);

        return topPairs;
    }
}
