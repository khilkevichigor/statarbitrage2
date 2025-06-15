package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.threecommas.response.bot.DcaBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateService {
    private static final String LONG_DCA_BOT_NAME = "stat_long";
    private static final String SHORT_DCA_BOT_NAME = "stat_short";
    private final SettingsService settingsService;

    public void validateSizeOfPairsAndThrow(List<ZScoreData> zScoreDataList, int size) {
        if (zScoreDataList.size() != size) {
            log.error("Size {} of pair {} not equal 1", zScoreDataList.size(), zScoreDataList);
            throw new IllegalArgumentException(String.format("Size %s not equal 1!", zScoreDataList.size()));
        }
    }

    public void validateCandlesLimitAndThrow(Map<String, List<Candle>> candlesMap) {
        if (candlesMap == null) {
            throw new IllegalArgumentException("Candles map cannot be null!");
        }

        Settings settings = settingsService.getSettings();
        int candleLimit = settings.getCandleLimit();

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
        long maxAgeMillis = Duration.ofMinutes(5).toMillis(); // допустим, 5 минут
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
}
