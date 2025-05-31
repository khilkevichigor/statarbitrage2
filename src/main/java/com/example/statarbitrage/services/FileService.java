package com.example.statarbitrage.services;

import com.example.statarbitrage.adapters.ZonedDateTimeAdapter;
import com.example.statarbitrage.model.Candle;
import com.example.statarbitrage.model.EntryData;
import com.example.statarbitrage.model.Settings;
import com.example.statarbitrage.model.ZScoreEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SETTINGS_JSON_FILE_PATH = "settings.json";
    private static final String ENTRY_DATA_JSON_FILE_PATH = "entry_data.json";
    private static final String CANDLES_JSON_FILE_PATH = "candles.json";
    private static final String Z_SCORE_JSON_FILE_PATH = "z_score.json";
    private static final String CHARTS_DIR = "charts";

    public ZScoreEntry getTopPairEntry() {
        int maxAttempts = 5;
        int waitMillis = 300;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            List<ZScoreEntry> zScores = readZScoreJson();
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

    public EntryData getEntryData() {
        List<EntryData> entryData = readEntryDataJson();
        if (entryData == null || entryData.isEmpty()) {
            String message = "⚠️entry_data.json пустой или не найден";
            log.warn(message);
            throw new RuntimeException(message);
        }
        return entryData.get(0);
    }

    public void writeEntryDataToJson(List<EntryData> entries) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(ENTRY_DATA_JSON_FILE_PATH), entries);
        } catch (Exception e) {
            log.error("Ошибка при записи entry_data.json: {}", e.getMessage(), e);
        }
    }

//    public void writeAllClosesToJson(ConcurrentHashMap<String, List<Double>> topPairCloses) {
//        try {
//            MAPPER.writeValue(new File(ALL_CLOSES_JSON_FILE_PATH), topPairCloses);
//        } catch (IOException e) {
//            log.error("Ошибка при сохранении all_closes.json: {}", e.getMessage(), e);
//        }
//    }

    public void writeCandlesToJson(Map<String, List<Candle>> candles) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
                .setPrettyPrinting()
                .create();

        try (FileWriter file = new FileWriter(CANDLES_JSON_FILE_PATH)) {
            gson.toJson(candles, file);
        } catch (IOException e) {
            log.error("Ошибка при сохранении candles.json: {}", e.getMessage(), e);
        }
    }

    private List<ZScoreEntry> readZScoreJson() {
        try {
            return MAPPER.readValue(new File(Z_SCORE_JSON_FILE_PATH), new TypeReference<>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<EntryData> readEntryDataJson() {
        try {
            return MAPPER.readValue(new File(ENTRY_DATA_JSON_FILE_PATH), new TypeReference<>() {
            });
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void clearChartDir() {
        clearDirectory(CHARTS_DIR);
    }

    private void clearDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        clearDirectory(file.getAbsolutePath());
                    }
                    if (!file.delete()) {
                        log.warn("Не удалось удалить файл: {}", file.getAbsolutePath());
                    }
                }
            }
        }
    }

    public File getChart() {
        File chartDir = new File(CHARTS_DIR);
        File[] chartFiles = chartDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

        if (chartFiles != null && chartFiles.length > 0) {
            // Сортировка по времени последнего изменения (от новых к старым)
            Arrays.sort(chartFiles, Comparator.comparingLong(File::lastModified).reversed());
            return chartFiles[0]; // Самый свежий чарт
        }

        return null; // Если файлов нет
    }

    public void deleteSpecificFilesInProjectRoot(List<String> fileNames) {
        File projectRoot = new File(".");
        for (String fileName : fileNames) {
            File file = new File(projectRoot, fileName);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.info("✅ Удалён файл: {}", file.getAbsolutePath());
                } else {
                    log.warn("⚠️ Не удалось удалить файл: {}", file.getAbsolutePath());
                }
            } else {
                log.info("ℹ️ Файл не найден: {}", file.getAbsolutePath());
            }
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
        String zScorePath = "z_score.json";
        try {
            File zFile = new File(zScorePath);
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
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при фильтрации z_score.json: {}", e.getMessage(), e);
        }
    }

    public void saveSettings(Map<Long, Settings> userSettings) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(SETTINGS_JSON_FILE_PATH), userSettings);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<Long, Settings> loadSettings() {
        File file = new File(SETTINGS_JSON_FILE_PATH);
        if (file.exists()) {
            try {
                return MAPPER.readValue(file, new TypeReference<>() {
                });
            } catch (IOException e) {
                log.error("Ошибка при получении settings.json: {}", e.getMessage(), e);
                throw new RuntimeException("Ошибка при получении settings.json");
            }
        }
        return null;
    }
}