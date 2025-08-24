package com.example.core.core.services;

import com.example.shared.models.PairData;
import com.example.shared.models.Settings;
import com.example.shared.models.TradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
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
                String longTicker = tickers[0].trim();
                String shortTicker = tickers[1].trim();

                if (longTicker.equalsIgnoreCase(shortTicker)) {
                    log.warn("⚠️ Пропускаем пару {}/{}: тикеры не могут быть одинаковыми.", longTicker, shortTicker);
                    continue;
                }

                PairData pairData = new PairData(longTicker, shortTicker);
                pairData.setStatus(TradeStatus.OBSERVED);
                pairDataList.add(pairData);
            }
        }

        pairDataService.saveAll(pairDataList);
    }
}
