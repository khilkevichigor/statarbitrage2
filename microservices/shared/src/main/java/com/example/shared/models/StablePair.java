package com.example.shared.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "stable_pairs")
@NoArgsConstructor
@ToString
@Slf4j
public class StablePair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker_a", nullable = false, length = 20)
    private String tickerA;

    @Column(name = "ticker_b", nullable = false, length = 20)
    private String tickerB;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "stability_rating", length = 20)
    private String stabilityRating; // EXCELLENT, GOOD, MARGINAL, POOR, REJECTED, FAILED

    @Column(name = "is_tradeable")
    private Boolean isTradeable;

    @Column(name = "data_points")
    private Integer dataPoints;

    @Column(name = "candle_count")
    private Integer candleCount;

    @Column(name = "analysis_time_seconds")
    private Double analysisTimeSeconds;

    @Column(name = "timeframe", length = 10)
    private String timeframe; // 1m, 5m, 15m, 1H, 4H, 1D, 1W, 1M

    @Column(name = "period", length = 20)
    private String period; // день, неделя, месяц, 1 год, 2 года, 3 года

    @Column(name = "search_date", nullable = false)
    private LocalDateTime searchDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Настройки поиска в JSON формате
    @Column(name = "search_settings", columnDefinition = "TEXT")
    private String searchSettings;

    // Результаты анализа в JSON формате для сохранения всех деталей
    @Column(name = "analysis_results", columnDefinition = "TEXT")
    private String analysisResults;

    @Column(name = "is_in_monitoring", nullable = false)
    private Boolean isInMonitoring = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (searchDate == null) {
            searchDate = LocalDateTime.now();
        }
    }

    // Utility методы для работы с JSON полями
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public void setSearchSettingsMap(Map<String, Object> settings) {
        try {
            this.searchSettings = objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации настроек поиска", e);
            this.searchSettings = "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSearchSettingsMap() {
        if (searchSettings == null || searchSettings.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(searchSettings, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при десериализации настроек поиска", e);
            return Map.of();
        }
    }

    public void setAnalysisResultsMap(Map<String, Object> results) {
        try {
            this.analysisResults = objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при сериализации результатов анализа", e);
            this.analysisResults = "{}";
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAnalysisResultsMap() {
        if (analysisResults == null || analysisResults.isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(analysisResults, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Ошибка при десериализации результатов анализа", e);
            return Map.of();
        }
    }

    public String getPairName() {
        return tickerA + "/" + tickerB;
    }

    // Конструктор для создания из DTO (будет использоваться в core микросервисе)
    public static StablePair fromStabilityResult(Object stabilityResult,
                                                 String timeframe, String period, Map<String, Object> searchSettings) {
        StablePair stablePair = new StablePair();
        stablePair.setTimeframe(timeframe);
        stablePair.setPeriod(period);
        stablePair.setSearchSettingsMap(searchSettings);

        // Извлекаем данные из StabilityResultDto через рефлексию
        // поскольку shared модуль не имеет прямого доступа к core DTO классам
        try {
            Class<?> dtoClass = stabilityResult.getClass();

            // Извлекаем ticker_a и ticker_b
            java.lang.reflect.Field tickerAField = dtoClass.getDeclaredField("tickerA");
            tickerAField.setAccessible(true);
            String tickerA = (String) tickerAField.get(stabilityResult);
            stablePair.setTickerA(tickerA);

            java.lang.reflect.Field tickerBField = dtoClass.getDeclaredField("tickerB");
            tickerBField.setAccessible(true);
            String tickerB = (String) tickerBField.get(stabilityResult);
            stablePair.setTickerB(tickerB);

            // Извлекаем остальные поля
            java.lang.reflect.Field totalScoreField = dtoClass.getDeclaredField("totalScore");
            totalScoreField.setAccessible(true);
            Integer totalScore = (Integer) totalScoreField.get(stabilityResult);
            stablePair.setTotalScore(totalScore);

            java.lang.reflect.Field stabilityRatingField = dtoClass.getDeclaredField("stabilityRating");
            stabilityRatingField.setAccessible(true);
            String stabilityRating = (String) stabilityRatingField.get(stabilityResult);
            stablePair.setStabilityRating(stabilityRating);

            java.lang.reflect.Field isTradeableField = dtoClass.getDeclaredField("isTradeable");
            isTradeableField.setAccessible(true);
            Boolean isTradeable = (Boolean) isTradeableField.get(stabilityResult);
            stablePair.setIsTradeable(isTradeable);

            java.lang.reflect.Field dataPointsField = dtoClass.getDeclaredField("dataPoints");
            dataPointsField.setAccessible(true);
            Integer dataPoints = (Integer) dataPointsField.get(stabilityResult);
            stablePair.setDataPoints(dataPoints);

            java.lang.reflect.Field analysisTimeField = dtoClass.getDeclaredField("analysisTimeSeconds");
            analysisTimeField.setAccessible(true);
            Double analysisTimeSeconds = (Double) analysisTimeField.get(stabilityResult);
            stablePair.setAnalysisTimeSeconds(analysisTimeSeconds);

            // Устанавливаем candleCount равным dataPoints (количество точек данных = количество свечей)
            stablePair.setCandleCount(dataPoints);
            
            // Сохраняем весь объект в JSON формате для analysisResults
            Map<String, Object> analysisResultsMap = new HashMap<>();
            analysisResultsMap.put("tickerA", tickerA);
            analysisResultsMap.put("tickerB", tickerB);
            analysisResultsMap.put("totalScore", totalScore);
            analysisResultsMap.put("stabilityRating", stabilityRating);
            analysisResultsMap.put("isTradeable", isTradeable);
            analysisResultsMap.put("dataPoints", dataPoints);
            analysisResultsMap.put("candleCount", dataPoints);
            analysisResultsMap.put("analysisTimeSeconds", analysisTimeSeconds);

            // Пытаемся извлечь дополнительные поля если они есть
            try {
                java.lang.reflect.Field blockScoresField = dtoClass.getDeclaredField("blockScores");
                blockScoresField.setAccessible(true);
                Object blockScores = blockScoresField.get(stabilityResult);
                analysisResultsMap.put("blockScores", blockScores);
            } catch (Exception e) {
                // Поле может отсутствовать
            }

            try {
                java.lang.reflect.Field qualityMetricsField = dtoClass.getDeclaredField("qualityMetrics");
                qualityMetricsField.setAccessible(true);
                Object qualityMetrics = qualityMetricsField.get(stabilityResult);
                analysisResultsMap.put("qualityMetrics", qualityMetrics);
            } catch (Exception e) {
                // Поле может отсутствовать
            }

            try {
                java.lang.reflect.Field redFlagsField = dtoClass.getDeclaredField("redFlags");
                redFlagsField.setAccessible(true);
                Object redFlags = redFlagsField.get(stabilityResult);
                analysisResultsMap.put("redFlags", redFlags);
            } catch (Exception e) {
                // Поле может отсутствовать
            }

            try {
                java.lang.reflect.Field summaryField = dtoClass.getDeclaredField("summary");
                summaryField.setAccessible(true);
                Object summary = summaryField.get(stabilityResult);
                analysisResultsMap.put("summary", summary);
            } catch (Exception e) {
                // Поле может отсутствовать
            }

            stablePair.setAnalysisResultsMap(analysisResultsMap);

        } catch (Exception e) {
            log.error("❌ Ошибка при извлечении данных из StabilityResultDto: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать StablePair из результата анализа", e);
        }

        return stablePair;
    }
}