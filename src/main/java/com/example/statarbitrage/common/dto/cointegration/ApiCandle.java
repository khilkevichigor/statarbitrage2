package com.example.statarbitrage.common.dto.cointegration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiCandle {

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("close")
    private double close;
}