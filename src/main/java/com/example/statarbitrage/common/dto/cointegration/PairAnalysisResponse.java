package com.example.statarbitrage.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PairAnalysisResponse {
    private boolean success;
    private PairAnalysisResult result;
}