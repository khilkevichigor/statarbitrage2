package com.example.core.services;

import com.example.shared.models.Settings;
import com.example.shared.models.TradeStatus;
import com.example.shared.models.TradingPair;
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

    private final TradingPairService tradingPairService;
    private final SettingsService settingsService;

    @Transactional
    public void updateObservedPairs(String pairsString) {
        Settings settings = settingsService.getSettings();
        settings.setObservedPairs(pairsString);
        settingsService.save(settings);

        tradingPairService.deleteAllByStatus(TradeStatus.OBSERVED);

        if (pairsString == null || pairsString.isBlank()) {
            return;
        }

        List<String> pairs = Arrays.asList(pairsString.split(","));
        List<TradingPair> tradingPairList = new ArrayList<>();

        for (String pair : pairs) {
            String[] tickers = pair.split("/");
            if (tickers.length == 2) {
                String longTicker = tickers[0].trim();
                String shortTicker = tickers[1].trim();

                if (longTicker.equalsIgnoreCase(shortTicker)) {
                    log.warn("⚠️ Пропускаем пару {}/{}: тикеры не могут быть одинаковыми.", longTicker, shortTicker);
                    continue;
                }

                TradingPair tradingPair = new TradingPair(longTicker, shortTicker);
                tradingPair.setStatus(TradeStatus.OBSERVED);
                tradingPairList.add(tradingPair);
            }
        }

        tradingPairService.saveAll(tradingPairList);
    }
}
