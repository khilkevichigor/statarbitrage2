package com.example.statarbitrage.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CointegrationDetails {
    private Double trace_statistic;
    private Double critical_value_95;
    private List<Double> eigenvalues;
    private List<Double> cointegrating_vector;
    private String error;
}