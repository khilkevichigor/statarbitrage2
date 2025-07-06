package com.example.statarbitrage.common.dto.cointegration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiCandle {
    private long timestamp;
    private double close;
}