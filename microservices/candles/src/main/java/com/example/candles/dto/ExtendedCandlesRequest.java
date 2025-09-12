package com.example.candles.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса большого количества свечей с пагинацией
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendedCandlesRequest {

    /**
     * Таймфрейм (1m, 5m, 15m, 1h, 4h, 1D, 1W, 1M)
     */
    private String timeframe;

    /**
     * Требуемое количество свечей
     */
    private int candleLimit;

    /**
     * Настройки фильтрации (минимальный объем и др.)
     */
    private double minVolume;

    /**
     * Использовать ли фильтр по минимальному объему
     */
    private boolean useMinVolumeFilter;

    /**
     * Черный список тикеров для исключения
     */
    private String minimumLotBlacklist;
}