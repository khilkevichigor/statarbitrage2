package com.example.statarbitrage.services;

import com.example.statarbitrage.model.ZScoreEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZScoreService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String Z_SCORE_JSON_FILE_PATH = "z_score.json";

    public ZScoreEntry getBestPair() {
        int maxAttempts = 5;
        int waitMillis = 300;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            List<ZScoreEntry> zScores = loadZscore();
            if (zScores != null && !zScores.isEmpty()) {
                if (zScores.size() == 1) {
                    return zScores.get(0);
                }
                ZScoreEntry bestPair = getBest(zScores);
                save(Collections.singletonList(bestPair));
                return bestPair;
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

    public void save(List<ZScoreEntry> entries) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(Z_SCORE_JSON_FILE_PATH), entries);
        } catch (Exception e) {
            log.error("Ошибка при записи z_score.json: {}", e.getMessage(), e);
        }
    }

    public ZScoreEntry getBest(List<ZScoreEntry> zScores) {
        return getBestPairByZscoreAndPvalue(zScores);
//        return getPairWithMaxZScore(zScores);
    }

    public ZScoreEntry getBestPairByZscoreAndPvalue(List<ZScoreEntry> zScores) {
        return zScores.stream()
                .min((e1, e2) -> {
                    int cmp = Double.compare(e1.getPvalue(), e2.getPvalue());
                    if (cmp == 0) {
                        //При равных pvalue берём с большим zscore
                        return -Double.compare(e1.getZscore(), e2.getZscore());
                    }
                    return cmp;
                })
                .orElse(null);
    }

    public ZScoreEntry getPairWithMaxZScore(List<ZScoreEntry> zScores) {
        return zScores.stream()
                .max(Comparator.comparingDouble(e -> Math.abs(e.getZscore())))
                .orElse(null);
    }

    public List<ZScoreEntry> loadZscore() {
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
