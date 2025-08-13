package com.example.statarbitrage.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZScoreHistoryItem {

    @JsonProperty("index")
    private Integer index;

    @JsonProperty("zscore")
    private Double zscore;

    @JsonProperty("adf_pvalue")
    private Double adf_pvalue;

    @JsonProperty("setr_squaredtings")
    private Double r_squared;

    @JsonProperty("timestamp")
    private Long timestamp;
}