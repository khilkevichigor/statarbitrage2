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
                    latest.getLongticker(), latest.getShortticker(),
                    latest.getPvalue(), latest.getAdfpvalue(), latest.getZscore(), latest.getCorrelation()
            ));
            return best;
        } else {
            throw new IllegalArgumentException("Отобрано 0 пар");
        }
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
            String longTicker = data.getLongticker();
            String shortTicker = data.getShortticker();

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


}
