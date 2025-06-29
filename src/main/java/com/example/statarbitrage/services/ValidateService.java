package com.example.statarbitrage.services;

import com.example.statarbitrage.model.*;
import com.example.statarbitrage.model.threecommas.response.bot.DcaBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.example.statarbitrage.constant.Constants.LONG_DCA_BOT_NAME;

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

    public boolean isLastZLessThenMinZ(List<ZScoreData> zScoreDataList, Settings settings) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            throw new IllegalArgumentException("ZScoreData list is null or empty");
        }

        for (ZScoreData zScoreData : zScoreDataList) {
            if (zScoreData == null || zScoreData.getZscoreParams() == null || zScoreData.getZscoreParams().isEmpty()) {
                throw new IllegalArgumentException("ZScoreData or its zscoreParams is null or empty");
            }

            List<ZScoreParam> zscoreParams = zScoreData.getZscoreParams();
            ZScoreParam latestParam = zscoreParams.get(zscoreParams.size() - 1);

            double latestZ = latestParam.getZscore();
            if (latestZ < settings.getMinZ()) {
                log.warn("Skip this pair {{}} - {{}}. Z-score {{}} < minZ {{}}",
                        zScoreData.getLongTicker(), zScoreData.getShortTicker(), latestZ, settings.getMinZ());
                return true;
            }
        }
        return false;
    }

    public void validatePositiveZAndThrow(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            throw new IllegalArgumentException("ZScoreData list is null or empty");
        }

        for (ZScoreData zScoreData : zScoreDataList) {
            if (zScoreData == null || zScoreData.getZscoreParams() == null || zScoreData.getZscoreParams().isEmpty()) {
                throw new IllegalArgumentException("ZScoreData or its zscoreParams is null or empty");
            }

            List<ZScoreParam> zscoreParams = zScoreData.getZscoreParams();
            ZScoreParam latestParam = zscoreParams.get(zscoreParams.size() - 1);

            double latestZ = latestParam.getZscore();
            if (latestZ < 0) {
                log.warn("Z-score is negative: {} (long: {}, short: {})",
                        latestZ, zScoreData.getLongTicker(), zScoreData.getShortTicker());
                throw new IllegalStateException("Latest Z-score must be non-negative, but was: " + latestZ);
            }
        }
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

    public void validatePairDataAndThrow(PairData pairData) {
        if (pairData == null || pairData.getLongTicker() == null || pairData.getShortTicker() == null) {
            log.error("PairData, longTicker or shortTicker cannot be null!");
            throw new IllegalArgumentException("PairData, longTicker or shortTicker cannot be null!");
        }

        long currentTime = System.currentTimeMillis();
        long maxAgeMillis = Duration.ofMinutes(60).toMillis(); // допустим, 5 минут
        if (pairData.getZScoreParams() == null ||
                pairData.getZScoreParams().isEmpty() ||
                pairData.getZScoreParams().get(pairData.getZScoreParams().size() - 1).getTimestamp() < (currentTime - maxAgeMillis)) {

            log.error("PairData cannot be old!");
            throw new IllegalArgumentException("PairData cannot be old!");
        }

//        if (pairData.getZScoreCurrent() < 0 || pairData.getZScoreCurrent() > 100) { //todo бывает с -
//            log.error("PairData, zScoreCurrent is out of range!");
//        }

        if (pairData.getCorrelationCurrent() < 0.8) {
            throw new IllegalArgumentException("Correlation must be greater than 0.8!");
        }
    }

    public void validateLongBotBeforeNewTradeAndThrow(DcaBot dcaBot) {
        if (dcaBot == null) {
            throw new IllegalArgumentException("Long dcaBot cannot be null!");
        }
        if (!Objects.equals(dcaBot.getName(), LONG_DCA_BOT_NAME)) {
            throw new IllegalArgumentException("Long bot has wrong name");
        }
        if (dcaBot.getPairs() == null || dcaBot.getPairs().size() != 1) {
            throw new IllegalArgumentException("Long dcaBot has wrong pairs count");
        }
        if (dcaBot.getBaseOrderVolume() == null || !dcaBot.getBaseOrderVolume().equals("20.0")) {
            throw new IllegalArgumentException("Long dcaBot has wrong baseOrderVolume");
        }
        if (dcaBot.getBaseOrderVolumeType() == null || !dcaBot.getBaseOrderVolumeType().equals("quote_currency")) {
            throw new IllegalArgumentException("Long dcaBot has wrong baseOrderVolumeType");
        }
        if (dcaBot.getStrategy() == null || !dcaBot.getStrategy().equals("long")) {
            throw new IllegalArgumentException("Long dcaBot has wrong strategy");
        }
        if (dcaBot.getType() == null || !dcaBot.getType().equals("Bot::SingleBot")) {
            throw new IllegalArgumentException("Long dcaBot has wrong type");
        }
        if (dcaBot.isEnabled()) { //должен быть выключен после предыдущего трейда
            throw new IllegalArgumentException("Long dcaBot is enabled");
        }
    }

    public void validatePairsAndThrow(DcaBot dcaBot, String threeCommasTicker) {
        if (dcaBot == null || threeCommasTicker == null || dcaBot.getPairs() == null || dcaBot.getPairs().size() != 1 || !dcaBot.getPairs().get(0).equals(threeCommasTicker)) {
            throw new IllegalArgumentException("dcaBot has wrong pairs");
        }
    }

    public void validatePositiveZ(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) return;

        for (int i = 0; i < zScoreDataList.size(); i++) {
            ZScoreData data = zScoreDataList.get(i);
            List<ZScoreParam> zscoreParams = data.getZscoreParams();

            if (zscoreParams == null || zscoreParams.isEmpty()) {
                throw new RuntimeException("ZScoreParams list is null or empty at index " + i);
            }

            double lastZ = zscoreParams.get(zscoreParams.size() - 1).getZscore();
            if (lastZ < 0) {
                throw new RuntimeException("Last Z-score is negative at index " + i + ": " + lastZ);
            }
        }
    }

}
