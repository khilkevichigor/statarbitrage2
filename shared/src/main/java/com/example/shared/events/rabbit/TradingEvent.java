package com.example.shared.events.rabbit;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TradingEvent extends BaseEvent {
    private String symbol;
    private String action;
    private BigDecimal amount;
    private BigDecimal price;
    private String status;

    public TradingEvent(String symbol, String action, BigDecimal amount, BigDecimal price, String status) {
        super("TRADING_EVENT");
        this.symbol = symbol;
        this.action = action;
        this.amount = amount;
        this.price = price;
        this.status = status;
    }
}