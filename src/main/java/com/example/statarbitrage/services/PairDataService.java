package com.example.statarbitrage.services;

import com.example.statarbitrage.model.*;
import com.example.statarbitrage.repositories.PairDataRepository;
import com.example.statarbitrage.vaadin.services.TradeStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.statarbitrage.constant.Constants.PAIR_DATA_FILE_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairDataService {
    private final ChangesService changesService;
    private final ExitStrategyService exitStrategyService;
    private final PairDataRepository pairDataRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PairData createPairData(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
        PairData pairData = new PairData();

        pairData.setLongTicker(zScoreData.getLongTicker());
        pairData.setShortTicker(zScoreData.getShortTicker());

        pairData.setZScoreParams(zScoreData.getZscoreParams());

        pairData.setCandles(candlesMap);

        ZScoreParam latestParam = zScoreData.getZscoreParams().get(zScoreData.getZscoreParams().size() - 1);

        List<Candle> longTickerCandles = candlesMap.get(zScoreData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(zScoreData.getShortTicker());

        if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                shortTickerCandles == null || shortTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}", zScoreData.getLongTicker(), zScoreData.getShortTicker());
        }

        pairData.setLongTickerCurrentPrice(longTickerCandles.get(longTickerCandles.size() - 1).getClose());
        pairData.setShortTickerCurrentPrice(shortTickerCandles.get(shortTickerCandles.size() - 1).getClose());

        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        saveToJson(pairData);

        log.info("Создали pair_data.json");

        return pairData;
    }

    public List<PairData> createPairDataList(List<ZScoreData> top10, Map<String, List<Candle>> candlesMap) {
        List<PairData> result = new ArrayList<>();

        for (ZScoreData zScoreData : top10) {
            try {
                PairData pairData = createSinglePairData(zScoreData, candlesMap);
                result.add(pairData);
            } catch (Exception e) {
                log.error("Ошибка при создании PairData для пары {}/{}: {}",
                        zScoreData.getLongTicker(),
                        zScoreData.getShortTicker(),
                        e.getMessage());
            }
        }

        log.info("Создали данные для {} пар", result.size());
        return result;
    }

    private PairData createSinglePairData(ZScoreData zScoreData, Map<String, List<Candle>> candlesMap) {
        PairData pairData = new PairData();

        // Устанавливаем основные параметры
        pairData.setLongTicker(zScoreData.getLongTicker());
        pairData.setShortTicker(zScoreData.getShortTicker());
        pairData.setZScoreParams(zScoreData.getZscoreParams());
        pairData.setCandles(candlesMap);

        // Получаем последние параметры
        ZScoreParam latestParam = zScoreData.getZscoreParams().get(zScoreData.getZscoreParams().size() - 1);

        // Получаем свечи
        List<Candle> longTickerCandles = candlesMap.get(zScoreData.getLongTicker());
        List<Candle> shortTickerCandles = candlesMap.get(zScoreData.getShortTicker());

        // Проверяем наличие данных
        if (longTickerCandles == null || longTickerCandles.isEmpty() ||
                shortTickerCandles == null || shortTickerCandles.isEmpty()) {
            log.warn("Нет данных по свечам для пары: {} - {}",
                    zScoreData.getLongTicker(), zScoreData.getShortTicker());
            throw new IllegalArgumentException("Отсутствуют данные свечей");
        }

        // Устанавливаем текущие цены
        pairData.setLongTickerCurrentPrice(longTickerCandles.get(longTickerCandles.size() - 1).getClose());
        pairData.setShortTickerCurrentPrice(shortTickerCandles.get(shortTickerCandles.size() - 1).getClose());

        // Устанавливаем статистические параметры
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        // Дополнительные расчеты (если нужны)
        calculateAdditionalMetrics(pairData);

        return pairData;
    }

    private void calculateAdditionalMetrics(PairData pairData) {
        // Здесь можно добавить дополнительные расчеты метрик
        // Например:
        BigDecimal zScoreChanges = BigDecimal.valueOf(pairData.getZScoreCurrent())
                .subtract(BigDecimal.valueOf(pairData.getZScoreEntry()));
        pairData.setZScoreChanges(zScoreChanges);

        // Добавьте другие расчеты по необходимости
    }

    public PairData getPairData() {
        PairData pairData = readPairDataJson(PAIR_DATA_FILE_NAME);
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

    public void saveToJson(PairData pairData) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(PAIR_DATA_FILE_NAME), pairData);
        } catch (Exception e) {
            log.error("Ошибка при записи pair_data.json: {}", e.getMessage(), e);
        }
    }

    public void update(PairData pairData, ZScoreData zScoreData, Map<String, List<Candle>> candles) {
        pairData.setCandles(candles);

        //updateCurrentPrices
        List<Candle> longTickerCandles = candles.get(pairData.getLongTicker());
        List<Candle> shortTickerCandles = candles.get(pairData.getShortTicker());

        double aCurrentPrice = longTickerCandles.get(longTickerCandles.size() - 1).getClose();
        double bCurrentPrice = shortTickerCandles.get(shortTickerCandles.size() - 1).getClose();

        pairData.setLongTickerCurrentPrice(aCurrentPrice);
        pairData.setShortTickerCurrentPrice(bCurrentPrice);

        ZScoreParam latestParam = zScoreData.getZscoreParams().get(zScoreData.getZscoreParams().size() - 1);

        //setupEntryPointsIfNeeded
        if (pairData.getLongTickerEntryPrice() == 0.0 || pairData.getShortTickerEntryPrice() == 0.0) {
            pairData.setLongTickerEntryPrice(aCurrentPrice);
            pairData.setShortTickerEntryPrice(bCurrentPrice);

            pairData.setZScoreEntry(latestParam.getZscore());
            pairData.setCorrelationEntry(latestParam.getCorrelation());
            pairData.setAdfPvalueEntry(latestParam.getAdfpvalue());
            pairData.setPValueEntry(latestParam.getPvalue());
            pairData.setMeanEntry(latestParam.getMean());
            pairData.setStdEntry(latestParam.getStd());
            pairData.setSpreadEntry(latestParam.getSpread());
            pairData.setAlphaEntry(latestParam.getAlpha());
            pairData.setBetaEntry(latestParam.getBeta());

            // Ставим время открытия по long-свечке
            pairData.setEntryTime(latestParam.getTimestamp());

            log.info("🔹Установлены точки входа: LONG {{}} = {}, SHORT {{}} = {}, Z = {}",
                    pairData.getLongTicker(), pairData.getLongTickerEntryPrice(),
                    pairData.getShortTicker(), pairData.getShortTickerEntryPrice(),
                    pairData.getZScoreEntry());
        }

        //updateCurrentCointParams
        pairData.setZScoreCurrent(latestParam.getZscore());
        pairData.setCorrelationCurrent(latestParam.getCorrelation());
        pairData.setAdfPvalueCurrent(latestParam.getAdfpvalue());
        pairData.setPValueCurrent(latestParam.getPvalue());
        pairData.setMeanCurrent(latestParam.getMean());
        pairData.setStdCurrent(latestParam.getStd());
        pairData.setSpreadCurrent(latestParam.getSpread());
        pairData.setAlphaCurrent(latestParam.getAlpha());
        pairData.setBetaCurrent(latestParam.getBeta());

        //calculateAndSetChanges
        ChangesData changesData = changesService.calculate(pairData);
        pairData.setLongChanges(changesData.getLongReturnRounded());
        pairData.setShortChanges(changesData.getShortReturnRounded());
        pairData.setProfitChanges(changesData.getProfitRounded());
        pairData.setZScoreChanges(changesData.getZScoreRounded());
        pairData.setTimeInMinutesSinceEntryToMax(changesData.getTimeInMinutesSinceEntryToMax());
        pairData.setTimeInMinutesSinceEntryToMin(changesData.getTimeInMinutesSinceEntryToMin());
        pairData.setMinProfitRounded(changesData.getMinProfitRounded());
        pairData.setMaxProfitRounded(changesData.getMaxProfitRounded());

        pairData.setZScoreParams(zScoreData.getZscoreParams()); //обновляем

        pairData.setMinZ(changesData.getMinZ());
        pairData.setMaxZ(changesData.getMaxZ());
        pairData.setMinLong(changesData.getMinLong());
        pairData.setMaxLong(changesData.getMaxLong());
        pairData.setMinShort(changesData.getMinShort());
        pairData.setMaxShort(changesData.getMaxShort());
        pairData.setMinCorr(changesData.getMinCorr());
        pairData.setMaxCorr(changesData.getMaxCorr());

        pairData.setExitReason(exitStrategyService.getExitReason(pairData));

        pairData.setStatus(pairData.getExitReason() == null ? TradeStatus.TRADING : TradeStatus.CLOSED);

        saveToDb(pairData);
    }

    public void saveToDb(PairData pairData) {
        pairDataRepository.save(pairData);
    }

    public List<PairData> findAllByStatusOrderByEntryTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByEntryTimeDesc(status);
    }

    public List<PairData> findAllByStatusOrderByUpdatedTimeDesc(TradeStatus status) {
        return pairDataRepository.findAllByStatusOrderByUpdatedTimeDesc(status);
    }

    public int deleteAllByStatus(TradeStatus status) {
        return pairDataRepository.deleteAllByStatus(status);
    }
}
