package com.example.statarbitrage.services;

import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.ZScoreParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZScoreService {

    public ZScoreData obtainBest(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList != null && !zScoreDataList.isEmpty()) {
            log.info("Отобрано {} пар", zScoreDataList.size());
            ZScoreData best = getBestByCriteria(zScoreDataList);
            ZScoreParam latest = best.getZscoreParams().get(best.getZscoreParams().size() - 1); // последние params
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
    public List<ZScoreData> obtainTopNBestPairs(Settings settings, List<ZScoreData> zScoreDataList, int topN) {
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
            Optional<ZScoreData> maybeBest = getBestByCriteriaV2(settings, remainingPairs);
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
            ZScoreParam latest = pair.getZscoreParams().get(pair.getZscoreParams().size() - 1);
            log.info(String.format("%d. Пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                    i + 1,
                    pair.getLongTicker(), pair.getShortTicker(),
                    latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
            ));
        }
    }

    public List<ZScoreData> obtainTop10(List<ZScoreData> zScoreDataList) {
        if (zScoreDataList != null && !zScoreDataList.isEmpty()) {
            log.info("Отобрано {} пар", zScoreDataList.size());
            List<ZScoreData> top10 = getTop10ByCriteria(zScoreDataList);

            // Логируем информацию о топ-10 парах
            for (int i = 0; i < Math.min(top10.size(), 10); i++) {
                ZScoreData pair = top10.get(i);
                ZScoreParam latest = pair.getZscoreParams().get(pair.getZscoreParams().size() - 1);
                log.info(String.format("%d. Пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                        i + 1,
                        pair.getLongTicker(), pair.getShortTicker(),
                        latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
                ));
            }
            return top10;
        } else {
            throw new IllegalArgumentException("Отобрано 0 пар");
        }
    }

    private List<ZScoreData> getTop10ByCriteria(List<ZScoreData> zScoreData) {
        // Сортируем по нашим критериям
        zScoreData.sort((z1, z2) -> {
            if (z1.getZscoreParams() == null || z1.getZscoreParams().isEmpty()) return 1;
            if (z2.getZscoreParams() == null || z2.getZscoreParams().isEmpty()) return -1;

            ZScoreParam last1 = z1.getZscoreParams().get(z1.getZscoreParams().size() - 1);
            ZScoreParam last2 = z2.getZscoreParams().get(z2.getZscoreParams().size() - 1);

            // Сначала сравниваем по абсолютному значению zscore (по убыванию)
            int zScoreCompare = Double.compare(
                    Math.abs(last2.getZscore()),
                    Math.abs(last1.getZscore())
            );
            if (zScoreCompare != 0) return zScoreCompare;

            // Затем по pvalue (по возрастанию)
            int pValueCompare = Double.compare(last1.getPvalue(), last2.getPvalue());
            if (pValueCompare != 0) return pValueCompare;

            // Затем по adfpvalue (по возрастанию)
            int adfCompare = Double.compare(last1.getAdfpvalue(), last2.getAdfpvalue());
            if (adfCompare != 0) return adfCompare;

            // Наконец по корреляции (по убыванию)
            return Double.compare(last2.getCorrelation(), last1.getCorrelation());
        });

        // Фильтруем пары с пустыми параметрами
        List<ZScoreData> filtered = zScoreData.stream()
                .filter(z -> z.getZscoreParams() != null && !z.getZscoreParams().isEmpty())
                .collect(Collectors.toList());

        // Возвращаем топ-10 или меньше, если данных недостаточно
        return filtered.stream().limit(10).collect(Collectors.toList());
    }

    private ZScoreData getBestByCriteria(List<ZScoreData> zScoreData) {
        ZScoreData best = null;

        for (ZScoreData z : zScoreData) {
            if (z.getZscoreParams() == null || z.getZscoreParams().isEmpty()) {
                continue;
            }
            ZScoreParam last = z.getZscoreParams().get(z.getZscoreParams().size() - 1); //уже отсортирован

            if (best == null) {
                best = z;
                continue;
            }

            ZScoreParam bestParam = best.getZscoreParams().get(best.getZscoreParams().size() - 1);

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

    private Optional<ZScoreData> getBestByCriteriaV2(Settings settings, List<ZScoreData> zScoreData) {
        if (zScoreData == null || zScoreData.isEmpty()) {
            return Optional.empty();
        }

        ZScoreData best = null;

        for (ZScoreData data : zScoreData) {
            if (data.getZscoreParams() == null || data.getZscoreParams().isEmpty()) {
                continue;
            }

            ZScoreParam last = data.getZscoreParams().get(data.getZscoreParams().size() - 1);

            // Фильтрация по минимальному |zscore|
            if (Math.abs(last.getZscore()) < settings.getExitZMin()) {
                log.info("Current Z < exitZmin");
                continue;
            }

            if (best == null) {
                best = data;
                continue;
            }

            ZScoreParam bestParam = best.getZscoreParams().get(best.getZscoreParams().size() - 1);

            // Сравнение по критериям
            if (Math.abs(last.getZscore()) > Math.abs(bestParam.getZscore())) {
                best = data;
            } else if (Math.abs(last.getZscore()) == Math.abs(bestParam.getZscore())) {
                if (last.getPvalue() < bestParam.getPvalue()) {
                    best = data;
                } else if (last.getPvalue() == bestParam.getPvalue()) {
                    if (last.getAdfpvalue() < bestParam.getAdfpvalue()) {
                        best = data;
                    } else if (last.getAdfpvalue() == bestParam.getAdfpvalue()) {
                        if (last.getCorrelation() > bestParam.getCorrelation()) {
                            best = data;
                        }
                    }
                }
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


}
