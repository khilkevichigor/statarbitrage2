package com.example.core.experemental.stability.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class StabilityResponseDto {
    
    private Boolean success;
    
    @JsonProperty("analysis_completed_at")
    private Double analysisCompletedAt;
    
    @JsonProperty("total_pairs_analyzed")
    private Integer totalPairsAnalyzed;
    
    @JsonProperty("tradeable_pairs_found")
    private Integer tradeablePairsFound;
    
    @JsonProperty("excellent_pairs_found")
    private Integer excellentPairsFound;
    
    @JsonProperty("analysis_time_seconds")
    private Double analysisTimeSeconds;
    
    @JsonProperty("settings_used")
    private Map<String, Object> settingsUsed;
    
    private List<StabilityResultDto> results;
    
    @JsonProperty("summary_stats")
    private StabilitySummaryStatsDto summaryStats;
}