package com.example.shared.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CandleEvent extends BaseEvent {
    private String symbol;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private String interval;
    
    public CandleEvent(String symbol, LocalDateTime openTime, LocalDateTime closeTime, 
                      BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, 
                      BigDecimal volume, String interval) {
        super("CANDLE_EVENT");
        this.symbol = symbol;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.interval = interval;
    }
}