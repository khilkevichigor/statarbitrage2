package com.example.core.experemental.stability.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class StabilitySummaryStatsDto {
    
    @JsonProperty("best_score")
    private Integer bestScore;
    
    @JsonProperty("average_score") 
    private Double averageScore;
    
    @JsonProperty("pairs_by_rating")
    private Map<String, Integer> pairsByRating;
}