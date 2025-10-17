package com.example.core.experemental.stability.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RedFlagsDto {
    
    @JsonProperty("low_correlation_flag")
    private Boolean lowCorrelationFlag;
    
    @JsonProperty("half_life_deterioration")
    private Boolean halfLifeDeterioration;
    
    @JsonProperty("new_historical_extremes")
    private Boolean newHistoricalExtremes;
    
    @JsonProperty("total_red_flags")
    private Integer totalRedFlags;
    
    @JsonProperty("has_critical_issues")
    private Boolean hasCriticalIssues;
    
    @JsonProperty("half_life_ratio")
    private Double halfLifeRatio;
    
    @JsonProperty("recent_max_vs_historical")
    private Double recentMaxVsHistorical;
    
    @JsonProperty("recent_min_vs_historical")
    private Double recentMinVsHistorical;
}