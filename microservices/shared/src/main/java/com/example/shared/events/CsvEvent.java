package com.example.shared.events;

import com.example.shared.models.TradingPair;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CsvEvent extends BaseEvent {
    private static final String BINDING_NAME = "csv-events-out-0";
    private TradingPair tradingPair;
    private Type type;

    public enum Type {
        EXPORT_CLOSED_PAIR
    }

    public CsvEvent(TradingPair tradingPair, Type type) {
        super("CSV_EVENT");
        super.setBindingName(BINDING_NAME);
        this.tradingPair = tradingPair;
        this.type = type;
    }
}