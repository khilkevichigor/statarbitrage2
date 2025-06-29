package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.ZScoreParam;
import com.example.statarbitrage.vaadin.python.PythonRestClient;
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
    private List<ZScoreData> calculateZScoreData(Settings settings,
                                                 Map<String, List<Candle>> candlesMap,
                                                 boolean excludeExistingPairs) {

        // Получаем результат из Python
        List<ZScoreData> rawZScoreList = PythonRestClient.fetchZScoreData(settings, candlesMap);
        if (rawZScoreList == null || rawZScoreList.isEmpty()) {
            log.warn("⚠️ ZScoreService: получен пустой список от Python");
            return Collections.emptyList();
        }

        // Убираем дубликаты по тикерам
//        reduceDuplicates(rawZScoreList); //todo удалить все последние -Z
//        removePairsWithNegativeLastZ(rawZScoreList); //todo можно даже так не делать тк мы берем бест с большим +Z

        // Применяем бизнес-валидации
//        handleNegativeZ(rawZScoreList);
//        validateService.validatePositiveZ(rawZScoreList);

        // Исключаем пары, которые уже в трейде
        if (excludeExistingPairs) {
            pairDataService.excludeExistingTradingPairs(rawZScoreList);
        }

        // Сортировка (можно кастомизировать)
        sortByLongTicker(rawZScoreList);
        sortParamsByTimestamp(rawZScoreList);

        return rawZScoreList;
    }

    public ZScoreData calculateZScoreDataOnUpdate(Settings settings,
                                                  Map<String, List<Candle>> candlesMap) {

        // Получаем результат из Python
        List<ZScoreData> rawZScoreList = PythonRestClient.fetchZScoreData(settings, candlesMap);
        if (rawZScoreList == null || rawZScoreList.isEmpty()) {
            log.warn("⚠️ ZScoreService: получен пустой список от Python");
            throw new IllegalStateException("⚠️ ZScoreService: получен пустой список от Python");
        }

        validateService.validateSizeOfPairsAndThrow(rawZScoreList, 1);

        return rawZScoreList.get(0);
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

    public ZScoreData obtainBest(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList != null && !zScoreDataList.isEmpty()) {
            log.info("Отобрано {} пар", zScoreDataList.size());
            ZScoreData best = getBestByCriteria(zScoreDataList);
            ZScoreParam latest = best.getLastZScoreParam(); // последние params
            log.info(String.format("Лучшая пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                    best.getLongTicker(), best.getShortTicker(),
                    latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
            ));
            return best;
        } else {
            throw new IllegalArgumentException("Отобрано 0 пар");
        }
    }

    //todo можно прикрутить стохастик и пересечение
    private List<ZScoreData> obtainTopNBestPairs(Settings settings, List<ZScoreData> zScoreDataList, int topN) {
        if (zScoreDataList == null || zScoreDataList.isEmpty()) {
            throw new IllegalArgumentException("Отобрано 0 пар");
        }
        if (topN <= 0 || topN > zScoreDataList.size()) {
            throw new IllegalArgumentException("Некорректное количество пар: " + topN);
        }

        log.info("Отобрано {} пар", zScoreDataList.size());
        List<ZScoreData> bestPairs = new ArrayList<>();
        List<ZScoreData> remainingPairs = new ArrayList<>(zScoreDataList); // копия списка

        for (int i = 0; i < topN; i++) {
            Optional<ZScoreData> maybeBest = getBestByCriteria(settings, remainingPairs);
            if (maybeBest.isPresent()) {
                ZScoreData best = maybeBest.get();
                bestPairs.add(best);
                remainingPairs.remove(best); // исключаем выбранную пару из дальнейшего отбора

            }
        }

        logBestPairs(bestPairs); // логируем топ-N пар
        return bestPairs;
    }

    private void logBestPairs(List<ZScoreData> bestPairs) {
        for (int i = 0; i < bestPairs.size(); i++) {
            ZScoreData pair = bestPairs.get(i);
            ZScoreParam latest = pair.getLastZScoreParam();
            log.info(String.format("%d. Пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                    i + 1,
                    pair.getLongTicker(), pair.getShortTicker(),
                    latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
            ));
        }
    }

    private ZScoreData getBestByCriteria(List<ZScoreData> zScoreData) {
        ZScoreData best = null;

        for (ZScoreData z : zScoreData) {
            if (z.getZscoreParams() == null || z.getZscoreParams().isEmpty()) {
                continue;
            }
            ZScoreParam last = z.getLastZScoreParam(); //уже отсортирован

            if (best == null) {
                best = z;
                continue;
            }

            ZScoreParam bestParam = best.getLastZScoreParam();

            // Сравниваем по критериям:

            // Больше |zscore| — лучше
            if (Math.abs(last.getZscore()) > Math.abs(bestParam.getZscore())) {
                best = z;
                continue;
            }
            if (Math.abs(last.getZscore()) < Math.abs(bestParam.getZscore())) {
                continue;
            }

            // Меньше pvalue — лучше
            if (last.getPvalue() < bestParam.getPvalue()) {
                best = z;
                continue;
            }
            if (last.getPvalue() > bestParam.getPvalue()) {
                continue;
            }

            // Меньше adfpvalue — лучше
            if (last.getAdfpvalue() < bestParam.getAdfpvalue()) {
                best = z;
                continue;
            }
            if (last.getAdfpvalue() > bestParam.getAdfpvalue()) {
                continue;
            }

            // Больше корреляция — лучше
            if (last.getCorrelation() > bestParam.getCorrelation()) {
                best = z;
            }
        }

        return best;
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

    public void reduceDuplicates(List<ZScoreData> zScoreDataList) {
        Map<String, ZScoreData> uniquePairs = new HashMap<>();

        for (ZScoreData data : zScoreDataList) {
            String longTicker = data.getLongTicker();
            String shortTicker = data.getShortTicker();

            String key = longTicker.compareTo(shortTicker) < 0 ? longTicker + "-" + shortTicker : shortTicker + "-" + longTicker;

            // если пары ещё нет или текущая упорядочена по алфавиту — кладём в мапу
            if (!uniquePairs.containsKey(key) || longTicker.compareTo(shortTicker) < 0) {
                uniquePairs.put(key, data);
            }
        }

        zScoreDataList.clear();
        zScoreDataList.addAll(uniquePairs.values());
    }

    public void removePairsWithNegativeLastZ(List<ZScoreData> zScoreDataList) {
        zScoreDataList.removeIf(data -> {
            List<ZScoreParam> params = data.getZscoreParams();
            if (params == null || params.isEmpty()) {
                return true; // нет данных — удаляем
            }
            double lastZ = params.get(params.size() - 1).getZscore();
            return lastZ < 0; // удаляем, если Z < 0
        });
    }


    public void sortParamsByTimestamp(List<ZScoreData> zScoreDataList) {
        zScoreDataList.forEach(zScoreData -> zScoreData.getZscoreParams().sort(Comparator.comparingLong(ZScoreParam::getTimestamp)));
    }

    public void sortParamsByTimestampV2(List<ZScoreData> zScoreDataList) {
        zScoreDataList.forEach(zScoreData -> {
            // Создаем новый изменяемый список и сортируем его
            List<ZScoreParam> mutableList = new ArrayList<>(zScoreData.getZscoreParams());
            mutableList.sort(Comparator.comparingLong(ZScoreParam::getTimestamp));
            // Заменяем исходный список на отсортированный
            zScoreData.setZscoreParams(mutableList);
        });
    }

    public void sortByLongTicker(List<ZScoreData> zScoreDataList) {
        zScoreDataList.sort(Comparator.comparing(ZScoreData::getLongTicker));
    }

    public void handleNegativeZ(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList == null) {
            return;
        }

        for (ZScoreData data : zScoreDataList) {
            List<ZScoreParam> params = data.getZscoreParams();
            if (params == null || params.isEmpty()) {
                continue;
            }

            // Берём последний zscore
            double lastZ = params.get(params.size() - 1).getZscore();

            if (lastZ < 0) {
                // Меняем местами long и short тикеры
                String oldLong = data.getLongTicker();
                data.setLongTicker(data.getShortTicker());
                data.setShortTicker(oldLong);

                // Инвертируем zscore во всех параметрах
                for (ZScoreParam param : params) {
                    param.setZscore(-param.getZscore());
                }
            }
        }
    }
}
