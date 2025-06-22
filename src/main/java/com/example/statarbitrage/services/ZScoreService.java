package com.example.statarbitrage.services;

import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.ZScoreParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public void sortByLongTicker(List<ZScoreData> zScoreDataList) {
        zScoreDataList.sort(Comparator.comparing(ZScoreData::getLongTicker));
    }


}
