package com.example.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для запроса большого количества свечей с пагинацией
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendedCandlesRequest {

    /**
     * Таймфрейм (1m, 5m, 15m, 1H, 4H, 1D, 1W, 1M)
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

    /**
     * Список конкретных тикеров для получения свечей.
     * Если null или пустой - будут получены все доступные тикеры
     */
    private List<String> tickers;

    /**
     * Список тикеров которые нужно исключить из результата.
     * Используется для исключения уже торгуемых тикеров при поиске новых пар
     */
    private List<String> excludeTickers;

    /**
     * Использовать ли кэш для получения свечей (по умолчанию true).
     * Если false - загружать свечи напрямую с OKX
     */
    private Boolean useCache;
}