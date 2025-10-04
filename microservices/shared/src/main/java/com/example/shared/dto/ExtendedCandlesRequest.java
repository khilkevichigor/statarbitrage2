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
//    private boolean useMinVolumeFilter; //todo выпилить после удаления неиспользуемых методов

//    /**
//     * Черный список тикеров для исключения
//     */
//    private String minimumLotBlacklist; //todo transfer to excludedTickers field

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
//    private Boolean useCache; //todo выпилить после удаления неиспользуемых методов

    /**
     * Дата ДО которой нужно получить свечи в формате ISO 8601 (2025-09-28T00:00:00Z).
     * Если не указана - используется текущая дата
     */
    private String untilDate;

    /**
     * Период для получения исторических данных (1 год, 6 месяцев, 1 месяц).
     * Используется вместо candleLimit когда нужно получить данные за определенный период
     */
    private String period;

    /**
     * Биржа для получения данных (по умолчанию OKX)
     */
    private String exchange;
}