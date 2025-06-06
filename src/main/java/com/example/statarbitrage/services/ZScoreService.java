package com.example.statarbitrage.services;

import com.example.statarbitrage.model.ZScoreEntry;
import com.example.statarbitrage.model.ZScoreTimeSeries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZScoreService {

    public ZScoreTimeSeries obtainBest(List<ZScoreTimeSeries> zScoreTimeSeries) {
        if (zScoreTimeSeries != null && !zScoreTimeSeries.isEmpty()) {
            log.info("Отобрано {} пар", zScoreTimeSeries.size());
            ZScoreTimeSeries bestPair = getBestPairByCriteria(zScoreTimeSeries);
            ZScoreEntry entry = bestPair.getEntries().get(bestPair.getEntries().size() - 1); // последний entry
            log.info(String.format("Лучшая пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f",
                    bestPair.getA(), bestPair.getB(),
                    entry.getPvalue(), entry.getAdfpvalue(), entry.getZscore(), entry.getCorrelation()
            ));
            return bestPair;
        } else {
            throw new IllegalArgumentException("Отобрано 0 пар");
        }
    }

    private ZScoreTimeSeries getBestPairByCriteria(List<ZScoreTimeSeries> zScoreTimeSeries) {
        ZScoreTimeSeries best = null;

        for (ZScoreTimeSeries z : zScoreTimeSeries) {
            if (z.getEntries() == null || z.getEntries().isEmpty()) {
                continue;
            }
            ZScoreEntry lastEntry = z.getEntries().get(z.getEntries().size() - 1);

            if (best == null) {
                best = z;
                continue;
            }

            ZScoreEntry bestEntry = best.getEntries().get(best.getEntries().size() - 1);

            // Сравниваем по критериям:

            // Больше |zscore| — лучше
            if (Math.abs(lastEntry.getZscore()) > Math.abs(bestEntry.getZscore())) {
                best = z;
                continue;
            }
            if (Math.abs(lastEntry.getZscore()) < Math.abs(bestEntry.getZscore())) {
                continue;
            }

            // Меньше pvalue — лучше
            if (lastEntry.getPvalue() < bestEntry.getPvalue()) {
                best = z;
                continue;
            }
            if (lastEntry.getPvalue() > bestEntry.getPvalue()) {
                continue;
            }

            // Меньше adfpvalue — лучше
            if (lastEntry.getAdfpvalue() < bestEntry.getAdfpvalue()) {
                best = z;
                continue;
            }
            if (lastEntry.getAdfpvalue() > bestEntry.getAdfpvalue()) {
                continue;
            }

            // Больше корреляция — лучше
            if (lastEntry.getCorrelation() > bestEntry.getCorrelation()) {
                best = z;
            }
        }

        return best;
    }
}
