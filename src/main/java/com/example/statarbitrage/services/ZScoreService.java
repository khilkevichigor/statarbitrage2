package com.example.statarbitrage.services;

import com.example.statarbitrage.model.ZScoreData;
import com.example.statarbitrage.model.ZScoreParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

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
                    latest.getA(), latest.getB(),
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

    public void sortByTickers(List<ZScoreData> zScoreDataList) {
        List<ZScoreData> filtered = zScoreDataList.stream()
                .filter(data -> data.getA().compareTo(data.getB()) < 0) //оставит btc-eth, и откинет eth-btc
                .toList();

        zScoreDataList.clear();
        zScoreDataList.addAll(filtered);
    }

    public void sortParamsByTimestamp(List<ZScoreData> zScoreDataList) {
        zScoreDataList.forEach(zScoreData -> zScoreData.getZscoreParams().sort(Comparator.comparingLong(ZScoreParam::getTimestamp)));
    }


}
