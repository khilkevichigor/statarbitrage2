package com.example.shared.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StatisticsEvent extends BaseEvent {
    private String symbol;
    private String metricType;
    private BigDecimal value;
    private String period;
    
    public StatisticsEvent(String symbol, String metricType, BigDecimal value, String period) {
        super("STATISTICS_EVENT");
        this.symbol = symbol;
        this.metricType = metricType;
        this.value = value;
        this.period = period;
    }
}