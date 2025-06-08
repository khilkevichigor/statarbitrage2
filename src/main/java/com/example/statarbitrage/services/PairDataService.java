package com.example.statarbitrage.services;

import com.example.statarbitrage.bot.TradeType;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.ChangesData;
import com.example.statarbitrage.model.PairData;
import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.utils.EntryDataUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final ChangesService changesService;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PAIR_DATA_JSON_FILE_PATH = "pair_data.json";

    public PairData createPairData(ZScoreEntry bestPair, ConcurrentHashMap<String, List<Candle>> candlesMap) {
        PairData pairData = new PairData();

        pairData.setA(bestPair.getA());
        pairData.setB(bestPair.getB());

        pairData.setLongTicker(bestPair.getLongticker()); //todo странно почему чарт z ниже 0 но лонг A! по идее z<0 -> лонг B!!!
        pairData.setShortTicker(bestPair.getShortticker());

        List<Candle> aTickerCandles = candlesMap.get(bestPair.getA());
        List<Candle> bTickerCandles = candlesMap.get(bestPair.getB());

        if (aTickerCandles == null || aTickerCandles.isEmpty() ||
                bTickerCandles == null || bTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}", bestPair.getA(), bestPair.getB());
        }

        pairData.setATickerCurrentPrice(aTickerCandles.get(aTickerCandles.size() - 1).getClose());
        pairData.setBTickerCurrentPrice(bTickerCandles.get(bTickerCandles.size() - 1).getClose());

        pairData.setZScoreCurrent(bestPair.getZscore());
        pairData.setCorrelationCurrent(bestPair.getCorrelation());
        pairData.setAdfPvalueCurrent(bestPair.getAdfpvalue());
        pairData.setPValueCurrent(bestPair.getPvalue());
        pairData.setMeanCurrent(bestPair.getMean());
        pairData.setStdCurrent(bestPair.getStd());
        pairData.setSpreadCurrent(bestPair.getSpread());
        pairData.setAlphaCurrent(bestPair.getAlpha());
        pairData.setBetaCurrent(bestPair.getBeta());

        save(pairData);

        log.info("Создали pair_data.json");

        return pairData;
    }

    public PairData getPairData() {
        PairData pairData = readPairDataJson(PAIR_DATA_JSON_FILE_PATH);
        if (pairData == null) {
            String message = "⚠️pair_data.json пустой или не найден";
            log.warn(message);
            throw new RuntimeException(message);
        }
        return pairData;
    }

    public PairData readPairDataJson(String entryDataJsonFilePath) {
        try {
            return MAPPER.readValue(new File(entryDataJsonFilePath), new TypeReference<>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void save(PairData pairData) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(PAIR_DATA_JSON_FILE_PATH), pairData);
        } catch (Exception e) {
            log.error("Ошибка при записи pair_data.json: {}", e.getMessage(), e);
        }
    }

    public void updatePairDataAndSave(PairData pairData, ZScoreEntry firstPair, ConcurrentHashMap<String, List<Candle>> candles, TradeType tradeType) {
        updateCurrentPrices(pairData, candles);
        updateCurrentCointParams(pairData, firstPair);
        setupEntryPointsIfNeeded(pairData, candles, tradeType);
        calculateAndSetChanges(pairData, tradeType);
        save(pairData);
    }

    public void updateCurrentPrices(PairData pairData, ConcurrentHashMap<String, List<Candle>> candles) {
        try {
            List<Candle> aTickerCandles = candles.get(pairData.getA());
            List<Candle> bTickerCandles = candles.get(pairData.getB());

            double aPrice = aTickerCandles.get(aTickerCandles.size() - 1).getClose();
            double bPrice = bTickerCandles.get(bTickerCandles.size() - 1).getClose();

            pairData.setATickerCurrentPrice(aPrice);
            pairData.setBTickerCurrentPrice(bPrice);
        } catch (Exception e) {
            log.error("Ошибка при обновлении текущих цен: {}", e.getMessage(), e);
        }
    }

    public void setupEntryPointsIfNeeded(PairData pairData, ConcurrentHashMap<String, List<Candle>> candles, TradeType tradeType) {
        if (pairData.getATickerEntryPrice() == 0.0 || pairData.getBTickerEntryPrice() == 0.0) {
            pairData.setATickerEntryPrice(pairData.getATickerCurrentPrice());
            pairData.setBTickerEntryPrice(pairData.getBTickerCurrentPrice());

            pairData.setZScoreEntry(pairData.getZScoreCurrent());
            pairData.setCorrelationEntry(pairData.getCorrelationCurrent());
            pairData.setAdfPvalueEntry(pairData.getAdfPvalueCurrent());
            pairData.setPValueEntry(pairData.getPValueCurrent());
            pairData.setMeanEntry(pairData.getMeanCurrent());
            pairData.setStdEntry(pairData.getStdCurrent());
            pairData.setSpreadEntry(pairData.getSpreadCurrent());
            pairData.setAlphaEntry(pairData.getAlphaCurrent());
            pairData.setBetaEntry(pairData.getBetaCurrent());

            // Ставим время открытия по long-свечке
            pairData.setEntryTime(getEntryTime(pairData.getA(), candles));

            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z = {}",
                    EntryDataUtil.getLongTicker(pairData, tradeType), EntryDataUtil.getLongTickerEntryPrice(pairData, tradeType),
                    EntryDataUtil.getShortTicker(pairData, tradeType), EntryDataUtil.getShortTickerEntryPrice(pairData, tradeType),
                    pairData.getZScoreEntry());
        }
    }

    private long getEntryTime(String longticker, ConcurrentHashMap<String, List<Candle>> candles) {
        List<Candle> longTickerCandles = candles.get(longticker);
        Candle longCandle = longTickerCandles.get(longTickerCandles.size() - 1);
        return longCandle.getTimestamp();
    }

    public void calculateAndSetChanges(PairData pairData, TradeType tradeType) {
        ChangesData changesData = changesService.calculate(pairData, tradeType);

        pairData.setLongChanges(changesData.getLongReturnRounded());
        pairData.setShortChanges(changesData.getShortReturnRounded());

        pairData.setProfitChanges(changesData.getProfitRounded());
        pairData.setZScoreChanges(changesData.getZScoreRounded());

        pairData.setTimeInMinutesSinceEntryToMin(changesData.getTimeInMinutesSinceEntryToMin());
        pairData.setTimeInMinutesSinceEntryToMax(changesData.getTimeInMinutesSinceEntryToMax());
    }

    public void updateCurrentCointParams(PairData pairData, ZScoreEntry firstPair) {
        pairData.setZScoreCurrent(firstPair.getZscore());
        pairData.setCorrelationCurrent(firstPair.getCorrelation());
        pairData.setAdfPvalueCurrent(firstPair.getAdfpvalue());
        pairData.setPValueCurrent(firstPair.getPvalue());
        pairData.setMeanCurrent(firstPair.getMean());
        pairData.setStdCurrent(firstPair.getStd());
        pairData.setSpreadCurrent(firstPair.getSpread());
        pairData.setAlphaCurrent(firstPair.getAlpha());
        pairData.setBetaCurrent(firstPair.getBeta());
    }
}
