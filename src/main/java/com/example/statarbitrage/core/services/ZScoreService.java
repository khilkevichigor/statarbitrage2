package com.example.statarbitrage.core.services;

import com.example.statarbitrage.client_python.PythonRestClient;
import com.example.statarbitrage.common.dto.Candle;
import com.example.statarbitrage.common.dto.ZScoreData;
import com.example.statarbitrage.common.dto.ZScoreParam;
import com.example.statarbitrage.common.model.PairData;
import com.example.statarbitrage.common.model.Settings;
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
    private final PythonRestClient pythonRestClient;

    /**
     * Считает Z для всех пар из свечей.
     */
    private List<ZScoreData> calculateZScoreData(Settings settings, Map<String, List<Candle>> candlesMap, boolean excludeExistingPairs) {
        List<ZScoreData> rawZScoreList = pythonRestClient.fetchZScoreData(settings, candlesMap); //ZScoreParams is null
        if (rawZScoreList == null || rawZScoreList.isEmpty()) {
            log.warn("⚠️ ZScoreService: получен пустой список от Python");
            return Collections.emptyList();
        }
        checkZScoreParamsSize(rawZScoreList);
        filterIncompleteZScoreParams(null, rawZScoreList, settings);
        if (excludeExistingPairs) {
            pairDataService.excludeExistingTradingPairs(rawZScoreList);
        }
        return rawZScoreList;
    }

    private void checkZScoreParamsSize(List<ZScoreData> rawZScoreList) {
        log.info("🔍 Проверка ZScore данных:");
        for (ZScoreData z : rawZScoreList) {
            List<ZScoreParam> params = z.getZscoreParams();
            int size = params != null ? params.size() : 0;
            String longTicker = z.getUndervaluedTicker();
            String shortTicker = z.getOvervaluedTicker();

            // Используем данные из нового API если zscoreParams отсутствуют
            double lastZ = size > 0 ? params.get(size - 1).getZscore() :
                    (z.getLatest_zscore() != null ? z.getLatest_zscore() : 0.0);
            int observations = size > 0 ? size :
                    (z.getTotal_observations() != null ? z.getTotal_observations() : 0);

            String msg = String.format(
                    "📊 Пара: %s / %s | Наблюдений: %d | Последний Z: %.2f",
                    longTicker, shortTicker, observations, lastZ
            );
            log.info(msg);
        }
    }

    private void filterIncompleteZScoreParams(PairData pairData, List<ZScoreData> zScoreDataList, Settings settings) {
        double expected = settings.getExpectedZParamsCount();
        log.info("🔍 Ожидаемое количество наблюдений: {}", expected);

        int before = zScoreDataList.size();

        zScoreDataList.removeIf(data -> {
            // Проверяем размер данных (используем новые поля API если zscoreParams отсутствуют)
            List<ZScoreParam> params = data.getZscoreParams();
            int actualSize = params != null ? params.size() :
                    (data.getTotal_observations() != null ? data.getTotal_observations() : 0);

            // Для нового API не проверяем количество наблюдений - данные уже агрегированы
            boolean isIncompleteBySize = false;
            if (params != null && !params.isEmpty()) {
                // Только для старого формата проверяем количество наблюдений
                isIncompleteBySize = actualSize < expected;
                if (isIncompleteBySize) {
                    if (pairData != null) {
                        pairDataService.delete(pairData);
                        log.warn("❌ Удалили пару {} / {} — наблюдений {} (ожидалось {})",
                                data.getUndervaluedTicker(), data.getOvervaluedTicker(), actualSize, expected);
                    }
                }
            }

            // Получаем последний Z-score (используем новые поля API если zscoreParams отсутствуют)
            double lastZScore;
            if (params != null && !params.isEmpty()) {
                lastZScore = params.get(params.size() - 1).getZscore();
            } else if (data.getLatest_zscore() != null) {
                lastZScore = data.getLatest_zscore();
            } else {
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("❌ Удалили пару {} / {} — отсутствует информация о Z-score",
                            data.getUndervaluedTicker(), data.getOvervaluedTicker());
                }
                return true;
            }

            boolean isIncompleteByZ = lastZScore < settings.getMinZ();
            if (isIncompleteByZ) {
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("❌ Удалили пару {} / {} — Z={} < MinZ={}",
                            data.getUndervaluedTicker(), data.getOvervaluedTicker(), lastZScore, settings.getMinZ());
                }
            }
            return isIncompleteBySize || isIncompleteByZ;
        });

        int after = zScoreDataList.size();
        log.info("✅ После фильтрации осталось {} из {} пар", after, before);
    }

    public ZScoreData calculateZScoreData(Settings settings, Map<String, List<Candle>> candlesMap) {
        // Получаем результат из Python
        ZScoreData zScoreData = pythonRestClient.analyzePair(candlesMap, settings, true);
        if (zScoreData == null) {
            log.warn("⚠️ Обновление трейда - zScoreData is null");
            throw new IllegalStateException("⚠️ ⚠️ Обновление трейда - zScoreData is null");
        }

        return zScoreData;
    }

    public Optional<ZScoreData> calculateZScoreDataForNewTrade(PairData pairData, Settings settings, Map<String, List<Candle>> candlesMap) {
        ZScoreData zScoreData = pythonRestClient.analyzePair(candlesMap, settings, true);
        if (zScoreData == null) {
            log.warn("⚠️ Обновление zScoreData перед созданием нового трейда! zScoreData is null");
            throw new IllegalStateException("⚠️ Обновление zScoreData перед созданием нового трейда! zScoreData is null");
        }

        List<ZScoreData> zScoreSingletonList = new ArrayList<>(Collections.singletonList(zScoreData));

        checkZScoreParamsSize(zScoreSingletonList);
        filterIncompleteZScoreParams(pairData, zScoreSingletonList, settings);

        return Optional.of(zScoreData);
    }

    /**
     * Возвращает топ-N лучших пар.
     */
    public List<ZScoreData> getTopNPairs(Settings settings,
                                         Map<String, List<Candle>> candlesMap,
                                         int count) {

        List<ZScoreData> all = calculateZScoreData(settings, candlesMap, true);
        return obtainTopNBestPairs(candlesMap, settings, all, count);
    }

    private List<ZScoreData> obtainTopNBestPairs(Map<String, List<Candle>> candlesMap, Settings settings, List<ZScoreData> zScoreDataList, int topN) {
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

                //детальная инфа
                ZScoreData detailedZScoreData = getDetailedZScoreData(best, candlesMap, settings);

                bestPairs.add(detailedZScoreData);
                remainingPairs.remove(best); // исключаем выбранную пару из дальнейшего отбора
            }
        }

        return bestPairs;
    }

    private ZScoreData getDetailedZScoreData(ZScoreData best, Map<String, List<Candle>> candlesMap, Settings settings) {
        String overvalued = best.getOvervaluedTicker();
        String undervalued = best.getUndervaluedTicker();

        if (overvalued == null || undervalued == null) {
            throw new IllegalArgumentException("Tickers in 'best' are not initialized");
        }

        log.info("🔍 Preparing pair analysis for: {} (undervalued) / {} (overvalued)", undervalued, overvalued);

        // Создаём новую карту только с нужными тикерами в правильном порядке
        Map<String, List<Candle>> filteredCandlesMap = new LinkedHashMap<>();

        // Проверяем наличие данных для каждого тикера
        if (!candlesMap.containsKey(undervalued)) {
            throw new IllegalArgumentException("Missing candles data for undervalued ticker: " + undervalued);
        }
        if (!candlesMap.containsKey(overvalued)) {
            throw new IllegalArgumentException("Missing candles data for overvalued ticker: " + overvalued);
        }

        // Добавляем тикеры в определённом порядке
        filteredCandlesMap.put(undervalued, candlesMap.get(undervalued));
        filteredCandlesMap.put(overvalued, candlesMap.get(overvalued));

        log.info("📊 Filtered candles map contains {} tickers: {}", filteredCandlesMap.size(), filteredCandlesMap.keySet());

        // Передаём отфильтрованные данные в Python
        ZScoreData zScoreData = pythonRestClient.analyzePair(filteredCandlesMap, settings, true);

        if (zScoreData.getLatest_zscore() < 0) {
            String message = String.format("Последний Z {%.2f} < 0 после \"/analyze-pair\" для получения детальной инфы о паре %s - %s!!!", zScoreData.getLatest_zscore(), undervalued, overvalued);
            log.error(message);
            throw new IllegalStateException(message);
        }
        return zScoreData;
    }

    private void logLastZ(ZScoreData zScoreData) {
        List<ZScoreParam> params = zScoreData.getZscoreParams();

        if (params != null && !params.isEmpty()) {
            // Используем старый формат с детальными параметрами
            int size = params.size();
            log.info("🧪 Последние 5 Z-параметров для {} / {}:", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker());
            log.info(String.format("%-5s %-8s %-10s %-10s %-20s", "N", "Z", "ADF", "Corr", "Timestamp"));

            for (int i = Math.max(0, size - 5); i < size; i++) {
                ZScoreParam p = params.get(i);
                log.info(String.format(
                        "%-5d %-8.2f %-10.4f %-10.2f %-20s",
                        i + 1, p.getZscore(), p.getAdfpvalue(), p.getCorrelation(), p.getTimestamp()
                ));
            }
        } else {
            // Используем новый формат с агрегированными данными
            log.info("🧪 Статистика для {} / {}:", zScoreData.getUndervaluedTicker(), zScoreData.getOvervaluedTicker());
            log.info("  Latest Z-Score: {}", zScoreData.getLatest_zscore());
            log.info("  Correlation: {}", zScoreData.getCorrelation());
            log.info("  Correlation P-Value: {}", zScoreData.getCorrelation_pvalue());
            log.info("  Cointegration P-Value: {}", zScoreData.getCointegration_pvalue());
            log.info("  Total Observations: {}", zScoreData.getTotal_observations());
            log.info("  Avg R-Squared: {}", zScoreData.getAvg_r_squared());
        }
    }

    public Optional<ZScoreData> getBestByCriteria(Settings settings, List<ZScoreData> dataList) {
        ZScoreData best = null;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (ZScoreData z : dataList) {
            List<ZScoreParam> params = z.getZscoreParams();

            double zVal, pValue, adf, corr;

            if (params != null && !params.isEmpty()) {
                // Используем старый формат с детальными параметрами
                ZScoreParam last = params.get(params.size() - 1);
                zVal = last.getZscore();
                pValue = last.getPvalue();
                adf = last.getAdfpvalue();
                corr = last.getCorrelation();
            } else {
                // Используем новый формат с агрегированными данными
                if (z.getLatest_zscore() == null || z.getCorrelation() == null) continue;

                zVal = z.getLatest_zscore();
                corr = z.getCorrelation();

                // Для новых полей используем разумные значения по умолчанию
                pValue = z.getCorrelation_pvalue() != null ? z.getCorrelation_pvalue() : 0.0;
                adf = z.getCointegration_pvalue() != null ? z.getCointegration_pvalue() : 0.0;
            }

            // 1. Z >= minZ (только положительные Z-score, исключаем зеркальные пары)
            if (zVal < settings.getMinZ()) continue;

            // 2. pValue <= minPValue
            if (pValue > settings.getMinPvalue()) continue;

            // 3. adfValue <= minAdfValue
            if (adf > settings.getMinAdfValue()) continue;

            // 4. corr >= minCorr
            if (corr < settings.getMinCorrelation()) continue;

            // 5. Выбираем с максимальным Z (только положительные)
            if (zVal > maxZ) {
                maxZ = zVal;
                best = z;
            }
        }

        return Optional.ofNullable(best);
    }
}
