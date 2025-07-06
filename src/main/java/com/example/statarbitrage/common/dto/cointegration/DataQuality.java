package com.example.statarbitrage.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataQuality {
    private Double avg_r_squared;
    private Double avg_adf_pvalue;
    private Integer stable_periods;
}