package com.example.shared.events;

import com.example.shared.models.TradingPair;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CsvEvent extends BaseEvent {
    private TradingPair tradingPair;

    public CsvEvent(TradingPair tradingPair) {
        super("EXPORT_PAIR_DATA_REPORT");
        this.tradingPair = tradingPair;
    }
}