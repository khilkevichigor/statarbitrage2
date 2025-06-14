package com.example.statarbitrage.model.threecommas.response.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Trade {
    private String uuid;
    private long account_id;
    private String pair;
    private long created_at;
    private Long closed_at;
    private Order order;
    private Units units;
    private Price price;
    private Total total;
    private Conditional conditional;
    private Trailing trailing;
    private Timeout timeout;
    private Leverage leverage;
    private Status status;
    private Filled filled;
    private TradeData data;
}
