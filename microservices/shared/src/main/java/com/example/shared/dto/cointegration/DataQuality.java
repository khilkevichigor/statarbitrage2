package com.example.shared.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataQuality {

    @JsonProperty("avg_r_squared")
    private Double avg_r_squared;

    @JsonProperty("avg_adf_pvalue")
    private Double avg_adf_pvalue;

    @JsonProperty("stable_periods")
    private Integer stable_periods;
}