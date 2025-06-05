package com.example.statarbitrage.services;

import com.example.statarbitrage.model.ZScoreEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZScoreService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String Z_SCORE_JSON_FILE_PATH = "z_score.json";

    public ZScoreEntry obtainBestPair(List<ZScoreEntry> zScoreEntries) {
        if (zScoreEntries != null && !zScoreEntries.isEmpty()) {
            log.info("Отобрано {} пар", zScoreEntries.size());
            ZScoreEntry bestPair = getBestPairByCriteria(zScoreEntries);
            log.info(String.format("Лучшая пара: %s/%s | p=%.5f | adf=%.5f | z=%.2f | corr=%.2f\n",
                    bestPair.getA(), bestPair.getB(),
                    bestPair.getPvalue(), bestPair.getAdfpvalue(), bestPair.getZscore(), bestPair.getCorrelation()
            ));
            return bestPair;
        } else {
            throw new IllegalArgumentException("Отобрано 0 пар");
        }
    }

    public ZScoreEntry getFirstPair() {
        int maxAttempts = 5;
        int waitMillis = 300;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            List<ZScoreEntry> zScores = loadZscore();
            if (zScores != null && !zScores.isEmpty()) {
                return zScores.get(0);
            }
            log.warn("Попытка {}: z_score.json пустой или не найден", attempt);
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Ожидание чтения z_score.json прервано", e);
            }
        }

        log.error("❌ Не удалось прочитать z_score.json после {} попыток", maxAttempts);
        throw new RuntimeException("⚠️ z_score.json пустой или не найден после попыток");
    }

    private void save(List<ZScoreEntry> entries) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(Z_SCORE_JSON_FILE_PATH), entries);
        } catch (Exception e) {
            log.error("Ошибка при записи z_score.json: {}", e.getMessage(), e);
        }
    }

    private ZScoreEntry getBestPairByCriteria(List<ZScoreEntry> zScores) {
        return zScores.stream()
                .min(Comparator
                        .comparingDouble(ZScoreEntry::getPvalue)
                        .thenComparingDouble(ZScoreEntry::getAdfpvalue)
                        .thenComparing((e1, e2) ->
                                Double.compare(Math.abs(e2.getZscore()), Math.abs(e1.getZscore())))
                        .thenComparingDouble(ZScoreEntry::getCorrelation) // максимальная корреляция
                )
                .orElse(null);
    }

    private List<ZScoreEntry> loadZscore() {
        try {
            File zFile = new File(Z_SCORE_JSON_FILE_PATH);
            if (zFile.exists()) {
                return List.of(MAPPER.readValue(zFile, ZScoreEntry[].class));
            }
            return null;
        } catch (Exception e) {
            log.error("❌ Ошибка при получении z_score.json: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
