package com.example.statarbitrage.services;

import com.example.statarbitrage.api.PythonRestClient;
import com.example.statarbitrage.dto.Candle;
import com.example.statarbitrage.dto.ZScoreData;
import com.example.statarbitrage.dto.ZScoreParam;
import com.example.statarbitrage.model.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZScoreService {

    private final PairDataService pairDataService;
    private final ValidateService validateService;

    /**
     * Считает Z для всех пар из свечей.
     */
    private List<ZScoreData> calculateZScoreData(Settings settings, Map<String, List<Candle>> candlesMap, boolean excludeExistingPairs) {
        List<ZScoreData> rawZScoreList = PythonRestClient.fetchZScoreData(settings, candlesMap);
        if (rawZScoreList == null || rawZScoreList.isEmpty()) {
            log.warn("⚠️ ZScoreService: получен пустой список от Python");
            return Collections.emptyList();
        }
        checkZScoreParamsSize(rawZScoreList);
        filterIncompleteZScoreParams(rawZScoreList, settings);
        if (excludeExistingPairs) {
            pairDataService.excludeExistingTradingPairs(rawZScoreList);
        }
        return rawZScoreList;
    }

    private void checkZScoreParamsSize(List<ZScoreData> rawZScoreList) {
        log.info("🔍 Проверка размеров ZScoreParams:");
        for (ZScoreData z : rawZScoreList) {
            List<ZScoreParam> params = z.getZscoreParams();
            int size = params != null ? params.size() : 0;
            String longTicker = z.getLongTicker();
            String shortTicker = z.getShortTicker();

            double lastZ = size > 0 ? params.get(size - 1).getZscore() : 0.0;

            String msg = String.format(
                    "📊 Пара: %s / %s | Z-params: %d | Последний Z: %.2f",
                    longTicker, shortTicker, size, lastZ
            );
            log.info(msg);
        }
    }

    public void filterIncompleteZScoreParams(List<ZScoreData> zScoreDataList, Settings settings) {
        double expected = calculateExpectedZParamsCount(settings);
        log.info("🔍 Ожидаемое количество ZScoreParam: {}", expected);

        int before = zScoreDataList.size();

        zScoreDataList.removeIf(data -> {
            int actualSize = data.getZscoreParams().size();
            boolean isIncompleteBySize = actualSize < expected;
            if (isIncompleteBySize) {
                log.warn("❌ Удаляем пару {} / {} — Z-поинтов {} (ожидалось {})",
                        data.getLongTicker(), data.getShortTicker(), actualSize, expected);
            }

            boolean isIncompleteByZ = data.getLastZScoreParam().getZscore() <= settings.getMinZ();
            if (isIncompleteByZ) {
                log.warn("❌ Удаляем пару {} / {} — Z={} < MinZ={}",
                        data.getLongTicker(), data.getShortTicker(), data.getLastZScoreParam().getZscore(), settings.getMinZ());
            }
            return isIncompleteBySize || isIncompleteByZ;
        });

        int after = zScoreDataList.size();
        log.info("✅ После фильтрации осталось {} из {} пар", after, before);
    }


    public double calculateExpectedZParamsCount(Settings settings) {
        return settings.getCandleLimit() - settings.getMinWindowSize();
    }


    public ZScoreData calculateZScoreData(Settings settings, Map<String, List<Candle>> candlesMap) {

        // Получаем результат из Python
        List<ZScoreData> rawZScoreList = PythonRestClient.fetchZScoreData(settings, candlesMap);
        if (rawZScoreList == null || rawZScoreList.isEmpty()) {
            log.warn("⚠️ ZScoreService: получен пустой список от Python");
            throw new IllegalStateException("⚠️ ZScoreService: получен пустой список от Python");
        }

        validateService.validateSizeOfPairsAndThrow(rawZScoreList, 1);

        return rawZScoreList.get(0);
    }

    public Optional<ZScoreData> calculateZScoreDataForNewTrade(Settings settings, Map<String, List<Candle>> candlesMap) {
        List<ZScoreData> rawZScoreList = PythonRestClient.fetchZScoreData(settings, candlesMap);
        if (rawZScoreList == null || rawZScoreList.isEmpty()) {
            log.warn("⚠️ ZScoreService: получен пустой список от Python");
            throw new IllegalStateException("⚠️ ZScoreService: получен пустой список от Python");
        }

        checkZScoreParamsSize(rawZScoreList);
        filterIncompleteZScoreParams(rawZScoreList, settings);

        return rawZScoreList.size() == 1 ? Optional.of(rawZScoreList.get(0)) : Optional.empty();
    }

    /**
     * Возвращает топ-N лучших пар.
     */
    public List<ZScoreData> getTopNPairs(Settings settings,
                                         Map<String, List<Candle>> candlesMap,
                                         int count) {

        List<ZScoreData> all = calculateZScoreData(settings, candlesMap, true);
        return obtainTopNBestPairs(settings, all, count);
    }

    //todo можно прикрутить стохастик и пересечение
    private List<ZScoreData> obtainTopNBestPairs(Settings settings, List<ZScoreData> zScoreDataList, int topN) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            throw new IllegalArgumentException("Отобрано 0 пар");
        }
        if (topN <= 0) {
            throw new IllegalArgumentException("Некорректное количество пар: topN={" + topN + "}");
        }

        List<ZScoreData> bestPairs = new ArrayList<>();
        List<ZScoreData> remainingPairs = new ArrayList<>(zScoreDataList); // копия списка

        for (int i = 0; i < topN; i++) {
            Optional<ZScoreData> maybeBest = getBestByCriteria(settings, remainingPairs);
            if (maybeBest.isPresent()) {
                ZScoreData best = maybeBest.get();
                logLastZ(best);
                bestPairs.add(best);
                remainingPairs.remove(best); // исключаем выбранную пару из дальнейшего отбора
            }
        }

        return bestPairs;
    }

    private void logLastZ(ZScoreData zScoreData) {
        List<ZScoreParam> params = zScoreData.getZscoreParams();
        int size = params.size();

        log.info("🧪 Последние 5 Z-параметров для {} / {}:", zScoreData.getLongTicker(), zScoreData.getShortTicker());
        log.info(String.format("%-5s %-8s %-10s %-10s %-20s", "N", "Z", "ADF", "Corr", "Timestamp"));

        for (int i = Math.max(0, size - 5); i < size; i++) {
            ZScoreParam p = params.get(i);
            log.info(String.format(
                    "%-5d %-8.2f %-10.4f %-10.2f %-20s",
                    i + 1, p.getZscore(), p.getAdfpvalue(), p.getCorrelation(), p.getTimestamp()
            ));
        }
    }

    public Optional<ZScoreData> getBestByCriteria(Settings settings, List<ZScoreData> dataList) {
        ZScoreData best = null;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (ZScoreData z : dataList) {
            List<ZScoreParam> params = z.getZscoreParams();
            if (params == null || params.isEmpty()) continue;

            ZScoreParam last = params.get(params.size() - 1);

            double zVal = last.getZscore();
            double pValue = last.getPvalue();
            double adf = last.getAdfpvalue();
            double corr = last.getCorrelation();

            // 1. Z >= minZ
            if (zVal < settings.getMinZ()) continue;

            // 2. pValue <= minPValue
            if (pValue > settings.getMinPvalue()) continue;

            // 3. adfValue <= minAdfValue
            if (adf > settings.getMinAdfValue()) continue;

            // 4. corr >= minCorr
            if (corr < settings.getMinCorrelation()) continue;

            // 5. Выбираем с максимальным Z
            if (zVal > maxZ) {
                maxZ = zVal;
                best = z;
            }
        }

        return Optional.ofNullable(best);
    }
}
