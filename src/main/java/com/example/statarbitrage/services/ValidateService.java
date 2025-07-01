package com.example.statarbitrage.services;

import com.example.statarbitrage.dto.Candle;
import com.example.statarbitrage.dto.ZScoreData;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateService {
    private final SettingsService settingsService;

    public void validateSizeOfPairsAndThrow(List<ZScoreData> zScoreDataList, int size) {
        if (zScoreDataList.size() != size) {
            log.error("Size {} of pair {} not equal 1", zScoreDataList.size(), zScoreDataList);
            throw new IllegalArgumentException(String.format("Size %s not equal 1!", zScoreDataList.size()));
        }
    }

    public boolean isLastZLessThenMinZ(PairData pairData, Settings settings) {
        if (pairData == null) {
            throw new IllegalArgumentException("pairData is null");
        }
        if (pairData.getZScoreCurrent() < settings.getMinZ()) {
            log.warn("Skip this pair {{}} - {{}}. Z-score {{}} < minZ {{}}", pairData.getLongTicker(), pairData.getShortTicker(), pairData.getZScoreCurrent(), settings.getMinZ());
            return true;
        }

        return false;
    }

    public void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap) {
        if (candlesMap == null) {
            throw new IllegalArgumentException("Candles map cannot be null!");
        }

        Settings settings = settingsService.getSettingsFromDb();
        double candleLimit = settings.getCandleLimit();

        candlesMap.forEach((ticker, candles) -> {
            if (candles == null) {
                log.error("Candles list for ticker {} is null!", ticker);
                throw new IllegalArgumentException("Candles list cannot be null for ticker: " + ticker);
            }
            if (candles.size() != candleLimit) {
                log.error(
                        "Candles size {} for ticker {} does not match limit {}",
                        candles.size(), ticker, candleLimit
                );
                throw new IllegalArgumentException(
                        String.format(
                                "Candles size for ticker %s is %d, but expected %d",
                                ticker, candles.size(), candleLimit
                        )
                );
            }
        });
    }
}