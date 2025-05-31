package com.example.statarbitrage.services;

import com.example.statarbitrage.model.ZScoreEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.StatUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZScoreService {
    private final ADFService adfService;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String Z_SCORE_JSON_FILE_PATH = "z_score.json";

    public ZScoreEntry getTopPairEntry() {
        int maxAttempts = 5;
        int waitMillis = 300;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            List<ZScoreEntry> zScores = readZScoreJson(Z_SCORE_JSON_FILE_PATH);
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

    public List<ZScoreEntry> readZScoreJson(String zScoreJsonFilePath) {
        try {
            return MAPPER.readValue(new File(zScoreJsonFilePath), new TypeReference<>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void keepBestPairByZscoreAndPvalue() {
        //Оставляем только одну лучшую пару по zscore/pvalue
        String zScorePath = "z_score.json";
        try {
            File zFile = new File(zScorePath);
            if (zFile.exists()) {
                List<ZScoreEntry> allEntries = List.of(MAPPER.readValue(zFile, ZScoreEntry[].class));

                //Фильтрация: минимальный pvalue и при равенстве — максимальный zscore
                ZScoreEntry best = allEntries.stream()
                        .min((e1, e2) -> {
                            int cmp = Double.compare(e1.getPvalue(), e2.getPvalue());
                            if (cmp == 0) {
                                //При равных pvalue берём с большим zscore
                                return -Double.compare(e1.getZscore(), e2.getZscore());
                            }
                            return cmp;
                        })
                        .orElse(null);

                if (best != null) {
                    MAPPER.writeValue(zFile, List.of(best));
                }
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при фильтрации z_score.json: {}", e.getMessage(), e);
        }
    }

    public void keepPairWithMaxZScore() {
        try {
            File zFile = new File(Z_SCORE_JSON_FILE_PATH);
            if (zFile.exists()) {
                List<ZScoreEntry> allEntries = List.of(MAPPER.readValue(zFile, ZScoreEntry[].class));
                if (allEntries.isEmpty()) {
                    log.error("z_score.json пустой");
                }

                // Находим пару с максимальным абсолютным значением z-score
                ZScoreEntry best = allEntries.stream()
                        .max(Comparator.comparingDouble(e -> Math.abs(e.getZscore())))
                        .orElse(null);

                if (best == null) {
                    log.error("Нет лучшей пары в z_score.json");
                }
                MAPPER.writeValue(zFile, List.of(best));
                log.info("🔍 Оставили лучшую пару в z_score.json: {}", best);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при фильтрации z_score.json: {}", e.getMessage(), e);
        }
    }

    public ZScoreEntry buildZScoreEntry(String t1, String t2, double[] residuals) {
        double mean = StatUtils.mean(residuals);
        double std = Math.sqrt(StatUtils.variance(residuals));
        double latestZ = (residuals[residuals.length - 1] - mean) / std;

        ZScoreEntry entry = new ZScoreEntry();
        entry.setLongticker(t1);
        entry.setShortticker(t2);
        entry.setSpread(residuals[residuals.length - 1]);
        entry.setMean(mean);
        entry.setZscore(latestZ);
        entry.setPvalue(adfService.calculatePValue(residuals)); // если реализовано
        entry.setTimestamp(System.currentTimeMillis());

        return entry;
    }


}
