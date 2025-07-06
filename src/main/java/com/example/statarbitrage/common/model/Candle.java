package com.example.statarbitrage.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Модель свечи для тестирования
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Candle {
    private Long timestamp;
    private Double close;
    private Double open;
    private Double high;
    private Double low;
    private Double volume;
}