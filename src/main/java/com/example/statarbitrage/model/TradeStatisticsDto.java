package com.example.statarbitrage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeStatisticsDto {
    private long tradesToday;
    private long tradesTotal;

    private BigDecimal avgProfitToday;
    private BigDecimal avgProfitTotal;

    private long exitByStopToday;
    private long exitByStopTotal;

    private long exitByTakeToday;
    private long exitByTakeTotal;

    private long exitByZMinToday;
    private long exitByZMinTotal;

    private long exitByZMaxToday;
    private long exitByZMaxTotal;

    private long exitByTimeToday;
    private long exitByTimeTotal;
}
