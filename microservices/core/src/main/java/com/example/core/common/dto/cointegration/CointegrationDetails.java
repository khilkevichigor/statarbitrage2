package com.example.core.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CointegrationDetails {

    @JsonProperty("trace_statistic")
    private Double trace_statistic;

    @JsonProperty("critical_value_95")
    private Double critical_value_95;

    @JsonProperty("eigenvalues")
    private List<Double> eigenvalues;

    @JsonProperty("cointegrating_vector")
    private List<Double> cointegrating_vector;

    @JsonProperty("error")
    private String error;
}