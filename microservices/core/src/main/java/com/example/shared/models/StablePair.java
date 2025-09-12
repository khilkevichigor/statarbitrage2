package com.example.shared.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
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

    @Column(name = "analysis_time_seconds")
    private Double analysisTimeSeconds;

    @Column(name = "timeframe", length = 10)
    private String timeframe; // 1m, 5m, 15m, 1h, 4h, 1D, 1W, 1M

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

    // Конструктор для создания из DTO
    public static StablePair fromStabilityResult(com.example.core.experemental.stability.dto.StabilityResultDto result,
                                                 String timeframe, String period, Map<String, Object> searchSettings) {
        StablePair stablePair = new StablePair();
        stablePair.setTickerA(result.getTickerA());
        stablePair.setTickerB(result.getTickerB());
        stablePair.setTotalScore(result.getTotalScore());
        stablePair.setStabilityRating(result.getStabilityRating());
        stablePair.setIsTradeable(result.getIsTradeable());
        stablePair.setDataPoints(result.getDataPoints());
        stablePair.setAnalysisTimeSeconds(result.getAnalysisTimeSeconds());
        stablePair.setTimeframe(timeframe);
        stablePair.setPeriod(period);
        stablePair.setSearchSettingsMap(searchSettings);
        
        // Сохраняем все результаты анализа
        Map<String, Object> analysisMap = Map.of(
            "blockScores", result.getBlockScores() != null ? result.getBlockScores() : Map.of(),
            "qualityMetrics", result.getQualityMetrics() != null ? result.getQualityMetrics() : Map.of(),
            "redFlags", result.getRedFlags() != null ? result.getRedFlags() : Map.of(),
            "summary", result.getSummary() != null ? result.getSummary() : Map.of(),
            "error", result.getError() != null ? result.getError() : ""
        );
        stablePair.setAnalysisResultsMap(analysisMap);
        
        return stablePair;
    }

    public String getPairName() {
        return tickerA + "/" + tickerB;
    }
}