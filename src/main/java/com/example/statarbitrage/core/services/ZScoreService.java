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
    private final PythonRestClient pythonRestClient;
    private final FilterIncompleteZScoreParamsService filterIncompleteZScoreParamsService;
    private final ObtainBestPairByCriteriaService obtainBestPairByCriteriaService;

    private void checkZScoreParamsSize(List<ZScoreData> rawZScoreList) {
        log.debug("🔍 Проверка ZScore данных:");
        for (ZScoreData z : rawZScoreList) {
            List<ZScoreParam> params = z.getZScoreHistory();
            int size = params != null ? params.size() : 0;
            String longTicker = z.getUnderValuedTicker();
            String shortTicker = z.getOverValuedTicker();

            // Используем данные из нового API если zscoreParams отсутствуют
            double lastZ = size > 0 ? params.get(size - 1).getZscore() :
                    (z.getLatestZScore() != null ? z.getLatestZScore() : 0.0);
            int observations = size > 0 ? size :
                    (z.getTotalObservations() != null ? z.getTotalObservations() : 0);

            String msg = String.format(
                    "📊 Пара: %s / %s | Наблюдений: %d | Последний Z: %.2f",
                    longTicker, shortTicker, observations, lastZ
            );
            log.debug(msg);
        }
    }

    private void filterIncompleteZScoreParams(PairData pairData, List<ZScoreData> zScoreDataList, Settings settings) {
        double expected = settings.getExpectedZParamsCount();
        double maxZScore = zScoreDataList.stream()
                .map(data -> (data.getZScoreHistory() != null && !data.getZScoreHistory().isEmpty()) ? data.getZScoreHistory().get(data.getZScoreHistory().size() - 1) : null)
                .filter(Objects::nonNull)
                .map(ZScoreParam::getZscore)
                .max(Comparator.naturalOrder())
                .orElse(0d);
        log.info("🔍 Ожидаемое количество наблюдений по настройкам: {}, максимальный Z-скор: {}", expected, maxZScore);

        int before = zScoreDataList.size();

        zScoreDataList.removeIf(data -> {
            // Проверяем размер данных (используем новые поля API если zscoreParams отсутствуют)
            List<ZScoreParam> params = data.getZScoreHistory();
            int actualSize = params != null ? params.size() :
                    (data.getTotalObservations() != null ? data.getTotalObservations() : 0);

            // Для нового API не проверяем количество наблюдений - данные уже агрегированы
            boolean isIncompleteBySize = false;
            if (params != null && !params.isEmpty()) {
                // Только для старого формата проверяем количество наблюдений
                isIncompleteBySize = actualSize < expected;
                if (isIncompleteBySize) {
                    if (pairData != null) {
                        pairDataService.delete(pairData);
                        log.warn("⚠️ Удалили пару {}/{} — наблюдений {} (ожидалось {})",
                                data.getUnderValuedTicker(), data.getOverValuedTicker(), actualSize, expected);
                    }
                }
            }

            // Получаем последний Z-score (используем новые поля API если zscoreParams отсутствуют)
            double lastZScore;
            if (params != null && !params.isEmpty()) {
                lastZScore = params.get(params.size() - 1).getZscore(); //todo
            } else if (data.getLatestZScore() != null) {
                lastZScore = data.getLatestZScore();
            } else {
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("⚠️ Удалили пару {}/{} — отсутствует информация о Z-score",
                            data.getUnderValuedTicker(), data.getOverValuedTicker());
                }
                return true;
            }

            boolean isIncompleteByZ = settings.isUseMinZFilter() && lastZScore < settings.getMinZ();
            if (isIncompleteByZ) {
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("⚠️ Удалили пару {}/{} — Z-скор={} < Z-скор Min={}",
                            data.getUnderValuedTicker(), data.getOverValuedTicker(), lastZScore, settings.getMinZ());
                }
            }

            // Фильтрация по R-squared
            boolean isIncompleteByRSquared = false;
            if (settings.isUseMinRSquaredFilter() && data.getAvgRSquared() != null && data.getAvgRSquared() < settings.getMinRSquared()) {
                isIncompleteByRSquared = true;
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("⚠️ Удалили пару {}/{} — RSquared={} < MinRSquared={}",
                            data.getUnderValuedTicker(), data.getOverValuedTicker(), data.getAvgRSquared(), settings.getMinRSquared());
                }
            }

            // Фильтрация по Correlation
            boolean isIncompleteByCorrelation = false;
            if (settings.isUseMinCorrelationFilter() && data.getPearsonCorr() != null && data.getPearsonCorr() < settings.getMinCorrelation()) {
                isIncompleteByCorrelation = true;
                if (pairData != null) {
                    pairDataService.delete(pairData);
                    log.warn("⚠️ Удалили пару {}/{} — Correlation={} < MinCorrelation={}",
                            data.getUnderValuedTicker(), data.getOverValuedTicker(), data.getPearsonCorr(), settings.getMinCorrelation());
                }
            }

            // Фильтрация по pValue
            boolean isIncompleteByPValue = false;
            if (settings.isUseMinPValueFilter()) {
                Double pValue = null;
                if (params != null && !params.isEmpty()) {
                    // Для старого формата используем pValue из последнего параметра
                    pValue = params.get(params.size() - 1).getPvalue();
                } else if (data.getPearsonCorrPValue() != null) {
                    // Для нового формата используем correlation_pvalue
                    pValue = data.getPearsonCorrPValue();
                }

                if (pValue != null && pValue > settings.getMinPValue()) {
                    isIncompleteByPValue = true;
                    if (pairData != null) {
                        pairDataService.delete(pairData);
                        log.warn("⚠️ Удалили пару {}/{} — pValue={} > MinPValue={}",
                                data.getUnderValuedTicker(), data.getOverValuedTicker(), pValue, settings.getMinPValue());
                    }
                }
            }

            // Фильтрация по adfValue
            boolean isIncompleteByAdfValue = false;
            if (settings.isUseMaxAdfValueFilter()) {
                Double adfValue = null;
                if (params != null && !params.isEmpty()) {
                    // Для старого формата используем adfpvalue из последнего параметра
                    adfValue = params.get(params.size() - 1).getAdfpvalue(); //todo здесь смесь старой и новой логики! актуализировать!!!
                } else if (data.getJohansenCointPValue() != null) {
                    // Для нового формата используем cointegration_pvalue
                    adfValue = data.getJohansenCointPValue(); //todo проверить это одно и то же???
                }

                if (adfValue != null && adfValue > settings.getMaxAdfValue()) {
                    isIncompleteByAdfValue = true;
                    if (pairData != null) {
                        pairDataService.delete(pairData);
                        log.warn("⚠️ Удалили пару {}/{} — adfValue={} > MaxAdfValue={}",
                                data.getUnderValuedTicker(), data.getOverValuedTicker(), adfValue, settings.getMaxAdfValue());
                    }
                }
            }

            return isIncompleteBySize || isIncompleteByZ || isIncompleteByRSquared || isIncompleteByCorrelation || isIncompleteByPValue || isIncompleteByAdfValue;
        });

        int after = zScoreDataList.size();
        log.debug("✅ После фильтрации осталось {} из {} пар", after, before);
    }

    public ZScoreData calculateZScoreData(Settings settings, Map<String, List<Candle>> candlesMap) {
        // Получаем результат из Python
        ZScoreData zScoreData = pythonRestClient.analyzePair(candlesMap, settings, true);
        if (zScoreData == null) {
            log.warn("⚠️ Обновление трейда - zScoreData is null");
            throw new IllegalStateException("⚠️ Обновление трейда - zScoreData is null");
        }

        return zScoreData;
    }

    /**
     * Считает Z для всех пар из свечей.
     */
    private List<ZScoreData> calculateZScoreData(Settings settings, Map<String, List<Candle>> candlesMap, boolean excludeExistingPairs) {

        // Получение коинтеграции
        List<ZScoreData> rawZScoreDataList = pythonRestClient.fetchZScoreData(settings, candlesMap); //ZScoreParams is null

        if (rawZScoreDataList == null || rawZScoreDataList.isEmpty()) {
            log.warn("⚠️ ZScoreService: получен пустой список от Python");
            return Collections.emptyList();
        }
        checkZScoreParamsSize(rawZScoreDataList);
//        filterIncompleteZScoreParams(null, rawZScoreDataList, settings);
        filterIncompleteZScoreParamsService.filterV2(rawZScoreDataList, settings);
        if (excludeExistingPairs) {
            pairDataService.excludeExistingTradingPairs(rawZScoreDataList);
        }
        return rawZScoreDataList;
    }

    public Optional<ZScoreData> calculateZScoreDataForNewTrade(PairData pairData, Settings settings, Map<String, List<Candle>> candlesMap) {
        ZScoreData zScoreData = pythonRestClient.analyzePair(candlesMap, settings, true);
        if (zScoreData == null) {
            log.warn("⚠️ Обновление zScoreData перед созданием нового трейда! zScoreData is null");
            throw new IllegalStateException("⚠️ Обновление zScoreData перед созданием нового трейда! zScoreData is null");
        }

        List<ZScoreData> zScoreDataSingletonList = new ArrayList<>(Collections.singletonList(zScoreData));
        checkZScoreParamsSize(zScoreDataSingletonList);

        // НЕ применяем фильтрацию для новых трейдов - пара уже была отфильтрована ранее в FetchPairsProcessor
        // filterIncompleteZScoreParamsService.filter(pairData, zScoreDataSingletonList, settings);
        log.debug("🔄 Обновление данных для уже отобранной пары {}", pairData.getPairName());

        return zScoreDataSingletonList.isEmpty() ? Optional.empty() : Optional.of(zScoreDataSingletonList.get(0));
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
        if (topN <= 0) {
            log.warn("⚠️ Некорректное количество пар: topN={}", topN);
            return Collections.emptyList();
        }
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            return Collections.emptyList();
        }

        List<ZScoreData> bestPairs = new ArrayList<>();
        List<ZScoreData> remainingPairs = new ArrayList<>(zScoreDataList); // копия списка

        for (int i = 0; i < topN; i++) {
//            Optional<ZScoreData> maybeBest = getBestByCriteria(settings, remainingPairs);
            Optional<ZScoreData> maybeBest = obtainBestPairByCriteriaService.getBestByCriteriaV2(settings, remainingPairs);
            if (maybeBest.isPresent()) {
                ZScoreData best = maybeBest.get();

                //смотрим что мы отобрали по тикерам
                List<String> actualBestTickers = new ArrayList<>();
                bestPairs.forEach(b -> {
                    actualBestTickers.add(b.getUnderValuedTicker());
                    actualBestTickers.add(b.getOverValuedTicker());
                });
                //берем только те новые тикеры которых еще нет в торговле
                if (actualBestTickers.contains(best.getUnderValuedTicker()) || actualBestTickers.contains(best.getOverValuedTicker())) {
                    log.warn("⚠️ Пропускаем пару {}/{} т.к. такие тикеры уже есть в торговле! Поддерживаем только уникальные тикеры для простоты ведения сделок!",
                            best.getUnderValuedTicker(), best.getOverValuedTicker());
                    continue;
                }

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
        String overvalued = best.getOverValuedTicker();
        String undervalued = best.getUnderValuedTicker();

        if (overvalued == null || undervalued == null) {
            throw new IllegalArgumentException("Тикеры в объекте 'best' не инициализированы");
        }

        log.debug("🔍 Подготовка парного анализа для: {} (undervalued) / {} (overvalued)", undervalued, overvalued);

        // Создаём новую карту только с нужными тикерами в правильном порядке
        Map<String, List<Candle>> filteredCandlesMap = new LinkedHashMap<>();

        // Проверяем наличие данных для каждого тикера
        if (!candlesMap.containsKey(undervalued)) {
            throw new IllegalArgumentException("❌ Отсутствуют данные свечей для undervalued тикера: " + undervalued);
        }
        if (!candlesMap.containsKey(overvalued)) {
            throw new IllegalArgumentException("❌ Отсутствуют данные свечей для overvalued тикера: " + overvalued);
        }

        // Добавляем тикеры в определённом порядке
        filteredCandlesMap.put(undervalued, candlesMap.get(undervalued));
        filteredCandlesMap.put(overvalued, candlesMap.get(overvalued));

        log.debug("📊 Отфильтрованная мапа свечей содержит тикеров: {{}} {}", filteredCandlesMap.size(), filteredCandlesMap.keySet());

        // Передаём отфильтрованные данные в Python
        ZScoreData zScoreData = pythonRestClient.analyzePair(filteredCandlesMap, settings, true);

        if (zScoreData.getLatestZScore() < 0) {
            String message = String.format("❌ Последний Z-скор {%.2f} < 0 после \"/analyze-pair\" для получения детальной инфы о паре %s - %s!!!", zScoreData.getLatestZScore(), undervalued, overvalued);
            log.error(message);
            throw new IllegalStateException(message);
        }
        return zScoreData;
    }

    private void logLastZ(ZScoreData zScoreData) {
        List<ZScoreParam> params = zScoreData.getZScoreHistory();

        if (params != null && !params.isEmpty()) {
            // Используем старый формат с детальными параметрами
            int size = params.size();
            log.debug("🧪 Последние 5 Z-параметров для {}/{}:", zScoreData.getUnderValuedTicker(), zScoreData.getOverValuedTicker());
            log.debug(String.format("% -5s % -8s % -10s % -10s % -20s", "N", "Z", "ADF", "Corr", "Timestamp"));

            for (int i = Math.max(0, size - 5); i < size; i++) {
                ZScoreParam p = params.get(i);
                log.debug(String.format(
                        "%-5d %-8.2f %-10.4f %-10.2f %-20s",
                        i + 1, p.getZscore(), p.getAdfpvalue(), p.getCorrelation(), p.getTimestamp()
                ));
            }
        } else {
            // Используем новый формат с агрегированными данными
            log.debug("🧪 Статистика для {}/{}:", zScoreData.getUnderValuedTicker(), zScoreData.getOverValuedTicker());
            log.debug("  Latest Z-Score: {}", zScoreData.getLatestZScore());
            log.debug("  Correlation: {}", zScoreData.getPearsonCorr());
            log.debug("  Correlation P-Value: {}", zScoreData.getPearsonCorrPValue());
            log.debug("  Cointegration P-Value: {}", zScoreData.getJohansenCointPValue());
            log.debug("  Total Observations: {}", zScoreData.getTotalObservations());
            log.debug("  Avg R-Squared: {}", zScoreData.getAvgRSquared());
        }
    }

    public Optional<ZScoreData> getBestByCriteria(Settings settings, List<ZScoreData> dataList) {
        ZScoreData best = null;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (ZScoreData z : dataList) {
            List<ZScoreParam> params = z.getZScoreHistory();

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
                if (z.getLatestZScore() == null || z.getPearsonCorr() == null) continue;

                zVal = z.getLatestZScore();
                corr = z.getPearsonCorr();

                // Для новых полей используем разумные значения по умолчанию
                pValue = z.getPearsonCorrPValue() != null ? z.getPearsonCorrPValue() : 0.0;
                adf = z.getJohansenCointPValue() != null ? z.getJohansenCointPValue() : 0.0;
            }

            // 1. Z >= minZ (только положительные Z-score, исключаем зеркальные пары)
            if (settings.isUseMinZFilter() && zVal < settings.getMinZ()) continue;

            // 2. pValue <= minPValue
            if (settings.isUseMinPValueFilter() && pValue > settings.getMinPValue()) continue;

            // 3. adfValue <= maxAdfValue
            if (settings.isUseMaxAdfValueFilter() && adf > settings.getMaxAdfValue()) continue;

            // 4. corr >= minCorr
            if (settings.isUseMinCorrelationFilter() && corr < settings.getMinCorrelation()) continue;

            // 5. Выбираем с максимальным Z (только положительные)
            if (zVal > maxZ) {
                maxZ = zVal;
                best = z;
            }
        }

        return Optional.ofNullable(best);
    }
}
