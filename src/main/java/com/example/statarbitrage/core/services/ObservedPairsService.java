package com.example.statarbitrage.core.services;

import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
import com.example.statarbitrage.common.model.TradeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObservedPairsService {

    private final PairDataService pairDataService;
    private final SettingsService settingsService;

    @Transactional
    public void updateObservedPairs(String pairsString) {
        Settings settings = settingsService.getSettings();
        settings.setObservedPairs(pairsString);
        settingsService.save(settings);

        pairDataService.deleteAllByStatus(TradeStatus.OBSERVED);

        if (pairsString == null || pairsString.isBlank()) {
            return;
        }

        List<String> pairs = Arrays.asList(pairsString.split(","));
        List<PairData> pairDataList = new ArrayList<>();

        for (String pair : pairs) {
            String[] tickers = pair.split("/");
            if (tickers.length == 2) {
                PairData pairData = new PairData(tickers[0].trim(), tickers[1].trim());
                pairData.setStatus(TradeStatus.OBSERVED);
                pairDataList.add(pairData);
            }
        }

        pairDataService.saveAll(pairDataList);
    }
}
