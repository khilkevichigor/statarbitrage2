package com.example.core.experemental.stability.dto;

import com.example.shared.enums.StabilityRating;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class StabilityResultDto {
    
    @JsonProperty("ticker_a")
    private String tickerA;
    
    @JsonProperty("ticker_b") 
    private String tickerB;
    
    @JsonProperty("total_score")
    private Integer totalScore;
    
    @JsonProperty("stability_rating")
    private StabilityRating stabilityRating;
    
    /**
     * Для обратной совместимости - сеттер принимает строку и конвертирует в enum
     */
    @JsonSetter("stability_rating")
    public void setStabilityRatingFromString(String value) {
        this.stabilityRating = StabilityRating.fromString(value);
    }
    
    @JsonProperty("is_tradeable")
    private Boolean isTradeable;
    
    @JsonProperty("data_points")
    private Integer dataPoints;
    
    @JsonProperty("analysis_time_seconds")
    private Double analysisTimeSeconds;
    
    @JsonProperty("block_scores")
    private Map<String, BlockAnalysisDto> blockScores;
    
    @JsonProperty("quality_metrics")
    private BlockAnalysisDto qualityMetrics;
    
    @JsonProperty("red_flags")
    private RedFlagsDto redFlags;
    
    private Map<String, Object> summary;
    
    private String error;
}